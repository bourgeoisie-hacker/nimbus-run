package com.nimbusrun.autoscaler.autoscaler;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.google.common.annotations.VisibleForTesting;
import com.nimbusrun.Utils;
import com.nimbusrun.autoscaler.metrics.MetricsContainer;
import com.nimbusrun.compute.ActionPool;
import com.nimbusrun.compute.Compute;
import com.nimbusrun.compute.Constants;
import com.nimbusrun.compute.DeleteInstanceRequest;
import com.nimbusrun.compute.ListInstanceResponse;
import com.nimbusrun.autoscaler.config.ConfigReader;
import com.nimbusrun.autoscaler.github.GithubService;
import com.nimbusrun.autoscaler.github.orm.runner.Runner;
import com.nimbusrun.compute.exceptions.InstanceCreateTimeoutException;
import com.nimbusrun.github.GithubActionJob;
import com.nimbusrun.github.WorkflowJobStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class Autoscaler {

    public static final Integer MAX_CREATE_FAILURE_RETRIES = 3;
    public static final Integer MAX_CREATE_POOL_FULL_RETRIES = 1000;

    private final ScheduledExecutorService mainThread;
    public BlockingDeque<UpscaleRequest> receivedRequests = new LinkedBlockingDeque<>();
    public BlockingDeque<GithubActionJob> receivedRetryRequests = new LinkedBlockingDeque<>();
    private final Map<String, ActionPool> actionPoolMap;
    private final Optional<ActionPool> defaultActionPool;
    private final MetricsContainer metricsContainer;
    private Compute compute;
    private GithubService githubService;
    private ConfigReader configReader;
    private final ExecutorService processMessageThread;
    private final ExecutorService virtualThreadPerTaskExecutor;
    private final Cache<String, AtomicBoolean> runnerLastBusy;
    private final Cache<DeleteInstanceRequest, AtomicInteger> instanceIdDeleteCounter;
    private final Cache<RunnerNameId, AtomicInteger> runnerIdDeleteCounter;
    private final Cache<String, AtomicInteger> actionPoolUpScale;

    public Autoscaler(Compute compute, GithubService githubService, ConfigReader configReader, MetricsContainer metricsContainer) throws InterruptedException {
        this.compute = compute;
        this.githubService = githubService;
        this.configReader = configReader;
        this.metricsContainer = metricsContainer;
        this.processMessageThread = Executors.newFixedThreadPool(2, Thread.ofVirtual().factory());
        this.mainThread = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());
        this.virtualThreadPerTaskExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.actionPoolMap = populateActionPoolMap();
        this.defaultActionPool = actionPoolMap.values().stream().filter(ActionPool::isDefault).findAny();
        this.mainThread.scheduleWithFixedDelay(this::handleComputeAndRunners,10,30, TimeUnit.SECONDS);
        // This operation could take longer
        var sc =this.mainThread.scheduleWithFixedDelay(this::updateInstanceCountGauge,10,30, TimeUnit.SECONDS);
        this.processMessageThread.execute(this::processMessage);
        this.processMessageThread.execute(this::processRetryMessage);

        this.runnerLastBusy = Caffeine.newBuilder()
                .maximumSize(1_000_000)
                .expireAfterWrite(Duration.ofHours(2))
                .build();
        this.instanceIdDeleteCounter = Caffeine.newBuilder()
                .maximumSize(1_000_000)
                .expireAfterWrite(Duration.ofMinutes(30))
                .build();
        this.runnerIdDeleteCounter = Caffeine.newBuilder()
                .maximumSize(1_000_000)
                .expireAfterWrite(Duration.ofMinutes(30))
                .build();
        this.actionPoolUpScale = Caffeine.newBuilder()
                .maximumSize(1_000_000)
                .expireAfterWrite(Duration.ofMinutes(1))
                .build();
        Thread.sleep(1000);

        log.info("Autoscaler started");
    }

    @VisibleForTesting
    Map<String, ActionPool> populateActionPoolMap() {
        return this.configReader.getActionPoolMap().values().stream().collect(Collectors.toMap(ActionPool::getName, Function.identity(), (a, b) -> b, ConcurrentHashMap::new));
    }



    public void retryLater(long waitInMilliseconds, UpscaleRequest upscaleRequest) {
        if(upscaleRequest.upScaleReason == UpScaleReason.RETRY_POOL_FULL){
            metricsContainer.instanceRetriesPoolFull(upscaleRequest.actionPool.getName());
        }else if (upscaleRequest.upScaleReason == UpScaleReason.RETRY_FAILED_CREATE){
            metricsContainer.instanceRetriesFailedCreate(upscaleRequest.actionPool.getName());
        }
        this.virtualThreadPerTaskExecutor.execute(() -> {
            try {
                Thread.sleep(waitInMilliseconds);
                this.receivedRequests.offer(upscaleRequest);
            } catch (InterruptedException e) {
            }
        });

    }

    /**
     * These are ran together so that we limit the number of times we must interact with github api.
     */
    public void handleComputeAndRunners() {
        try {
            log.info("Running handle Compute Runners");
            List<Runner> runners = githubService.listRunnersInGroup();
            Map<String, Runner> runnersMap = runners.stream().collect(Collectors.toMap(Runner::getName, Function.identity()));
            updateRunnerInfo(runners);
            scaleDownInstance(runnersMap);
            orphanedRunners(runners);
            deleteExpiredInstances();
            deleteExpiredRunners();
        }catch (Exception e){
            log.error("Failed the main loop ", e);
        }
    }
    public void updateInstanceCountGauge(){
        log.info("running update gauge");
        this.compute.listAllComputeInstances().forEach((key, insts)->
                metricsContainer.updateInstanceCount(key, insts.instances().size()));
    }

    public void updateRunnerInfo(List<Runner> runners) {
        try {
            runners.stream().forEach(r->{
                this.runnerLastBusy.get(r.getName()+"", key->new AtomicBoolean(true));
            });
        }catch (Exception e){
            log.error("error updating runner last busy due to: {}",e.getMessage());
        }
    }

    public void deleteExpiredInstances() {
        var map = new HashMap<>(instanceIdDeleteCounter.asMap());
        map.keySet().stream()
                .filter(key -> map.get(key).intValue() > Constants.DELETE_INSTANCE_RUNNER_THRESHOLD)
                .forEach(key -> {
                    this.virtualThreadPerTaskExecutor.execute(() -> {
                        try {
                            if(this.compute.deleteCompute(key)){
                                metricsContainer.instanceDeletedTotal(key.getActionPool().getName(), true);
                            }else {
                                metricsContainer.instanceDeletedTotal(key.getActionPool().getName(), false);
                            }
                        } catch (Exception e) {
                            metricsContainer.instanceDeletedTotal(key.getActionPool().getName(), false);
                            throw new RuntimeException(e);
                        }
                    });
                    try {
                        instanceIdDeleteCounter.invalidate(key);
                    } catch (NullPointerException e) {
                        log.error("Somehow the ben manes cache through a null pointer exception :(");
                    }
                });
    }

    public void deleteExpiredRunners() {
        var map = new HashMap<>(runnerIdDeleteCounter.asMap());
        map.keySet().stream()
                .filter(key -> map.get(key).intValue() > Constants.DELETE_INSTANCE_RUNNER_THRESHOLD)
                .forEach(key -> {
                    this.virtualThreadPerTaskExecutor.execute(() -> {
                        if(this.githubService.deleteRunner(key.id())){
                            log.info("Deleted orphaned runner name: {} id: {}", key.name(), key.id());
                        }
                    });
                    try {
                        runnerIdDeleteCounter.invalidate(key);
                    } catch (NullPointerException e) {
                        log.error("Somehow the ben manes cache through a null pointer exception :(");
                    }
                });
    }

    public void incrementInstanceIdDeleteCounter(DeleteInstanceRequest deleteInstanceRequest) {
        this.instanceIdDeleteCounter.get(deleteInstanceRequest, (key) -> new AtomicInteger(1)).incrementAndGet();
    }

    public void incrementRunnerIdDeleteCounter(Runner runner) {
        this.runnerIdDeleteCounter.get(new RunnerNameId(runner.getName(),runner.getId()+""), (key) -> new AtomicInteger(1)).incrementAndGet();

    }

    public synchronized void processRetryMessage(){
        while(true){
            log.debug("running retry");
            try{
                GithubActionJob gj;
                while((gj = this.receivedRetryRequests.poll(1,TimeUnit.MINUTES)) != null){
                    if(this.githubService.isJobQueued(gj.getRunUrl())){
                        log.info("Retrying payload {}", gj.getJsonStr());
                        receive(gj);
                    }
                }
            }catch (Exception e){}
        }
    }

    public synchronized void processMessage() {
        while (true) {
            try {
                log.debug("running process message");
                log.info("main Processes Started %s".formatted(((ThreadPoolExecutor)processMessageThread).getActiveCount()));
                log.info("Schedulged Processes Started %s and is shutdown? %s".formatted(((ThreadPoolExecutor)mainThread).getTaskCount(), mainThread.isShutdown()));

                UpscaleRequest upscaleRequest;
                while ((upscaleRequest = receivedRequests.poll(20, TimeUnit.SECONDS)) != null) {
                    if(upscaleRequest.getRetryCreateFailed() > MAX_CREATE_FAILURE_RETRIES || upscaleRequest.getRetryPoolFull() > MAX_CREATE_POOL_FULL_RETRIES){
                        log.info("Action pool %s not being expanded due to too many retries. pool full: %s and failed create: %s"
                                .formatted(upscaleRequest.actionPool.getName(),upscaleRequest.getRetryPoolFull(), upscaleRequest.getRetryCreateFailed()));
                        continue;
                    }
                    ActionPool pool = upscaleRequest.actionPool;
                    AtomicInteger numberOfInstances = actionPoolUpScale.get(pool.getName(), (k)-> new AtomicInteger(0));
                    int maxInstanceCount = pool.getMaxInstances().orElse(Constants.DEFAULT_MAX_INSTANCES);
                    boolean hasUnlimitedInstances = maxInstanceCount == 0;
                    ListInstanceResponse listInstanceResponse = compute.listComputeInstances(pool);

                    if (!hasUnlimitedInstances && (maxInstanceCount <= listInstanceResponse.instances().size()
                            || maxInstanceCount <= numberOfInstances.get())) {
                        this.retryLater(5000, upscaleRequest.retryPoolFull()); // So it doesn't occupy the CPU full time
                        continue;
                    }
                    ActionPool finalPool = pool;
                    UpscaleRequest finalUpscaleRequest = upscaleRequest;
                    numberOfInstances.incrementAndGet();

                    this.virtualThreadPerTaskExecutor.execute(() -> {
                        try {
                            log.info("Attempting to make instance for action pool: {}", finalPool.getName());
                            boolean successful = compute.createCompute(finalPool);
                            if(successful){
                                metricsContainer.instanceCreatedTotal(finalPool.getName(), true);
                            }else{
                                metricsContainer.instanceCreatedTotal(finalPool.getName(), false);

                                this.retryLater(5000, finalUpscaleRequest.retryCreateFailed());
                            }
                        } catch (InstanceCreateTimeoutException e){
                            metricsContainer.instanceCreatedTotal(finalPool.getName(), e.isShouldHaveBeenCreated());
                        } catch (Exception e) {
                            metricsContainer.instanceCreatedTotal(finalPool.getName(), false);
                            Utils.excessiveErrorLog("Failed to create compute instance for action pool %s due to %s".formatted(finalPool.getName(), e.getMessage()), e, log);
                        }
                    });
                }
            } catch (Exception e) {

            }
        }
    }

    public void orphanedRunners(List<Runner> runners){
        runners.stream()
                .filter(r-> "offline".equalsIgnoreCase(r.getStatus()))
                .peek(r-> log.debug("Incrementing delete counter for orphan runner {}", r.getName()))
                .forEach(r-> incrementRunnerIdDeleteCounter(r));
    }
    public void scaleDownInstance(Map<String, Runner> runners) {

        List<Callable<String>> callables = new ArrayList<>();
        this.actionPoolMap.forEach((key, actionPool) -> {
            callables.add(() -> {
                try {
                    ListInstanceResponse response = this.compute.listComputeInstances(actionPool);
                    response.instances().forEach(instance -> {
                        Runner runner = runners.get(instance.getName());
                        Boolean runnerComplete = null;
                        Boolean runnerBusy = null;
                        boolean instanceIdleTimeExceeded = false;
                        if (runner != null) {
                            runnerBusy = runner.isBusy();
                        }
                        if (this.runnerLastBusy.getIfPresent(instance.getInstanceName()) != null && runner == null) {
                            runnerComplete = true;
                        }
                        Duration instanceUpDuration = Duration.between(Instant.ofEpochMilli(instance.getInstanceCreateTimeInMilli()), Instant.now());
                        int idleTime = actionPool.getInstanceIdleScaleDownTimeInMinutes().orElse(Constants.DEFAULT_INSTANCE_IDLE_TIME_IN_MINUTES);
                        if (idleTime < instanceUpDuration.toMinutes()) {
                            instanceIdleTimeExceeded = true;
                        }
                        if (runnerBusy != null && runnerBusy) {
                            return;
                        }
                        if (Boolean.TRUE.equals(runnerComplete)) {
                            log.debug("incrementing delete counter for instance id: {}, name: {} due to runner complete.", instance.getInstanceId(), instance.getInstanceName());
                            incrementInstanceIdDeleteCounter(new DeleteInstanceRequest(actionPool,instance.getInstanceId(), instance.getInstanceName(), instance.getExtraProperties()));
                        } else if (instanceIdleTimeExceeded) {
                            log.debug("incrementing delete counter for instance id: {}, name: {} due to idle time exceeded.", instance.getInstanceId(), instance.getInstanceName());
                            incrementInstanceIdDeleteCounter(new DeleteInstanceRequest(actionPool,instance.getInstanceId(), instance.getInstanceName(), instance.getExtraProperties()));
                        }
                    });
                } catch (Exception e) {
                    log.error("Failed to evaluate scale down for action pool: %s, due to %s".formatted(actionPool.getName(), e.getMessage()), e);
                }
                return null;
            });

        });
        try {
            List<Future<String>> list = this.virtualThreadPerTaskExecutor.invokeAll(callables);
            for(var f: list){
                f.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to invoke scaled due to: {}", e.getMessage());
        }
    }


    /** So not to block kafka receiver the githubActionJob is offered to a dequeue
     *   for later processing in method {@link Autoscaler#processRetryMessage()}
     *
     * @param githubActionJob
     */
    public void receiveRetry(GithubActionJob githubActionJob){
        this.receivedRetryRequests.offer(githubActionJob);
    }

    public boolean receive(GithubActionJob githubActionJob) {
        if(githubActionJob.getStatus() != WorkflowJobStatus.QUEUED){
            return false;
        }
        ActionPool actionPool = null;
        boolean isForGroup = false;
        if(githubActionJob.getActionGroupName().isPresent() && githubActionJob.getActionGroupName().get().equalsIgnoreCase(this.githubService.getRunnerGroupName())){
            isForGroup = true;
        }
        if(githubActionJob.getActionPoolName().isPresent() && actionPoolMap.containsKey(githubActionJob.getActionPoolName().get())) {
            actionPool = actionPoolMap.getOrDefault(githubActionJob.getActionPoolName().get(), defaultActionPool.orElse(null));
        }
        if (isForGroup) {
            if(actionPool == null){
                log.warn("No action pool specified for workflow and no default action pool configured: {}", githubActionJob.getJsonStr());
                return false;
            }
            log.info("Received action pool request for {} and runner group: {}", actionPool.getName(), this.githubService.getRunnerGroupName());

            return receivedRequests.add(new UpscaleRequest(actionPool));
        }
        return false;
    }

    public enum UpScaleReason {
        NEW_REQUEST, RETRY_POOL_FULL, RETRY_FAILED_CREATE;


    }
    public static class UpscaleRequest{
        @Getter
        private int retryPoolFull;
        @Getter
        private int retryCreateFailed;
        @Getter
        private UpScaleReason upScaleReason;
        private final ActionPool actionPool;
        public UpscaleRequest(ActionPool actionPool ){
            this.retryPoolFull = 0;
            this.retryCreateFailed = 0;
            this.actionPool = actionPool;
        }

        public UpscaleRequest retryPoolFull(){
            upScaleReason = UpScaleReason.RETRY_POOL_FULL;
            this.retryPoolFull += 1;
            return this;
        }
        public UpscaleRequest retryCreateFailed(){
            upScaleReason = UpScaleReason.RETRY_POOL_FULL;
            this.retryCreateFailed += 1;
            return this;
        }


    }
    public record RunnerNameId(String name, String id){
        @Override
        public boolean equals(Object object) {
            if (object == null || getClass() != object.getClass()) return false;
            RunnerNameId that = (RunnerNameId) object;
            return Objects.equals(id, that.id) && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, id);
        }
    }
}
