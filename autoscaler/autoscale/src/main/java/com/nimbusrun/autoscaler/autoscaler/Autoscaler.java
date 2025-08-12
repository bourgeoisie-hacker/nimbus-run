package com.nimbusrun.autoscaler.autoscaler;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.google.common.annotations.VisibleForTesting;
import com.nimbusrun.Utils;
import com.nimbusrun.compute.ActionPool;
import com.nimbusrun.compute.Compute;
import com.nimbusrun.compute.Constants;
import com.nimbusrun.compute.DeleteInstanceRequest;
import com.nimbusrun.compute.ListInstanceResponse;
import com.nimbusrun.autoscaler.config.ConfigReader;
import com.nimbusrun.autoscaler.github.GithubService;
import com.nimbusrun.autoscaler.github.orm.runner.Runner;
import com.nimbusrun.github.GithubActionJob;
import com.nimbusrun.github.WorkflowJobStatus;
import io.micrometer.core.instrument.MeterRegistry;
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

    private final ScheduledExecutorService mainThread;
    public BlockingDeque<ActionPool> receivedRequests = new LinkedBlockingDeque<>();
    public BlockingDeque<GithubActionJob> receivedRetryRequests = new LinkedBlockingDeque<>();
    private final Map<String, ActionPool> actionPoolMap;
    private final Optional<ActionPool> defaultActionPool;
    private final MeterRegistry meterRegistry;
    private final Map<ActionPool, InstanceScalerTimeTracker> timeTrackerMap;
    private Compute compute;
    private GithubService githubService;
    private ConfigReader configReader;
    private final ExecutorService processMessageThread;
    private final ExecutorService virtualThreadPerTaskExecutor;
    private final Cache<String, AtomicBoolean> runnerLastBusy;
    private final Cache<DeleteInstanceRequest, AtomicInteger> instanceIdDeleteCounter;
    private final Cache<String, AtomicInteger> runnerIdDeleteCounter;

    public Autoscaler(Compute compute, GithubService githubService, ConfigReader configReader, MeterRegistry meterRegistry) {
        this.compute = compute;
        this.githubService = githubService;
        this.configReader = configReader;
        this.meterRegistry = meterRegistry;
        this.processMessageThread = Executors.newFixedThreadPool(2, Thread.ofVirtual().factory());
        this.mainThread = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
        this.virtualThreadPerTaskExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.actionPoolMap = populateActionPoolMap();
        this.timeTrackerMap = populateTimeTrackerMap();
        this.defaultActionPool = actionPoolMap.values().stream().filter(ActionPool::isDefault).findAny();

        this.mainThread.scheduleWithFixedDelay(this::handleComputeAndRunners,10,30, TimeUnit.SECONDS);
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
    }

    @VisibleForTesting
    Map<String, ActionPool> populateActionPoolMap() {
        return this.configReader.getActionPoolMap().values().stream().collect(Collectors.toMap(ActionPool::getName, Function.identity(), (a, b) -> b, ConcurrentHashMap::new));
    }

    @VisibleForTesting
    Map<ActionPool, InstanceScalerTimeTracker> populateTimeTrackerMap() {
        return this.configReader.getActionPoolMap().values()
                .stream()
                .collect(Collectors.toMap(Function.identity(), InstanceScalerTimeTracker::new,
                        (a, b) -> b, ConcurrentHashMap::new));
    }


    public void retryLater(long waitInMilliseconds, ActionPool actionPool) {
        this.virtualThreadPerTaskExecutor.execute(() -> {
            try {
                Thread.sleep(waitInMilliseconds);
                this.receivedRequests.offer(actionPool);
            } catch (InterruptedException e) {
            }
        });

    }

    public void handleComputeAndRunners() {
        try {
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
                            this.compute.deleteCompute(key);
                        } catch (Exception e) {
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
                        this.githubService.deleteRunner(key);
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

    public void incrementRunnerIdDeleteCounter(String runnerId) {
        this.runnerIdDeleteCounter.get(runnerId, (key) -> new AtomicInteger(1)).incrementAndGet();

    }

    public synchronized void processRetryMessage(){
        while(true){
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
                ActionPool pool;
                while ((pool = receivedRequests.poll(1, TimeUnit.MINUTES)) != null) {
                    InstanceScalerTimeTracker timeTracker = timeTrackerMap.get(pool);
                    Optional<Long> waitTime = timeTracker.getWaitTimeBeforeScaleUp(Constants.DEFAULT_TIME_BETWEEN_SCALE_UPS_IN_SECONDS);
                    if (waitTime.isPresent()) {
                        this.retryLater(waitTime.get(), pool); // So it doesn't occupy the CPU full time
                        continue;
                    }
                    ListInstanceResponse listInstanceResponse = compute.listComputeInstances(pool);

                    if (pool.getMaxInstances().orElse(Constants.DEFAULT_MAX_INSTANCES) < listInstanceResponse.instances().size()) {
                        this.retryLater(1000, pool); // So it doesn't occupy the CPU full time
                        continue;
                    }
                    timeTracker.setLastScaleUpTime(System.currentTimeMillis());
                    ActionPool finalPool = pool;
                    this.virtualThreadPerTaskExecutor.execute(() -> {
                        try {
                            boolean successful = compute.createCompute(finalPool);
                            if(successful){

                            }else{

                            }
                        } catch (Exception e) {
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
                .peek(r-> log.info("Incrementing delete counter for orphan runner {}", r.getName()))
                .map(Runner::getId)
                .forEach(r-> incrementRunnerIdDeleteCounter(r+""));
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
                            incrementInstanceIdDeleteCounter(new DeleteInstanceRequest(actionPool,instance.getInstanceId(), instance.getExtraProperties()));
                        } else if (instanceIdleTimeExceeded) {
                            log.debug("incrementing delete counter for instance id: {}, name: {} due to idle time exceeded.", instance.getInstanceId(), instance.getInstanceName());
                            incrementInstanceIdDeleteCounter(new DeleteInstanceRequest(actionPool,instance.getInstanceId(), instance.getExtraProperties()));
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
        for (String label : githubActionJob.getLabels()) {
            if (label.contains("=") && label.split("=").length == 2) {
                String key = label.split("=")[0];
                String value = label.split("=")[1];
                if (key.equalsIgnoreCase(Constants.ACTION_POOL_LABEL_KEY) && actionPoolMap.containsKey(value.toLowerCase()) && actionPool == null) {
                    actionPool = actionPoolMap.get(value);
                }
                if (key.equalsIgnoreCase(Constants.ACTION_GROUP_LABEL_KEY) && value.equalsIgnoreCase(this.githubService.getRunnerGroupName())) {
                    isForGroup = true;
                }
            }
        }
        if (isForGroup ) {
            ActionPool actionPoolToRun = Optional.ofNullable(actionPool).orElse(defaultActionPool.orElse(null));
            if(actionPoolToRun == null){
                log.warn("No action pool specified for workflow and no default action pool configured: {}", githubActionJob.getJsonStr());
                return false;
            }
            //(actionPool != null) check for default action pool
            log.info("Received action pool request for {} and runner group: {}", actionPool.getName(), this.githubService.getRunnerGroupName());
            return receivedRequests.add(actionPool);
        }
        return false;
    }

}
