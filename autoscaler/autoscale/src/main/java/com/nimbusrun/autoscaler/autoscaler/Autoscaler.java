package com.nimbusrun.autoscaler.autoscaler;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import com.nimbusrun.Utils;
import com.nimbusrun.autoscaler.github.GithubService;
import com.nimbusrun.autoscaler.github.orm.listDelivery.DeliveryRecord;
import com.nimbusrun.autoscaler.github.orm.runner.Runner;
import com.nimbusrun.autoscaler.metrics.MetricsContainer;
import com.nimbusrun.compute.ActionPool;
import com.nimbusrun.compute.Compute;
import com.nimbusrun.compute.Constants;
import com.nimbusrun.compute.DeleteInstanceRequest;
import com.nimbusrun.compute.ListInstanceResponse;
import com.nimbusrun.compute.exceptions.InstanceCreateTimeoutException;
import com.nimbusrun.config.ConfigReader;
import com.nimbusrun.github.GithubActionJob;
import com.nimbusrun.webhook.WebhookReceiver;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.ProtocolException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Autoscaler implements WebhookReceiver {

  public static final Integer MAX_CREATE_FAILURE_RETRIES = 3;
  public static final Integer MAX_CREATE_POOL_FULL_RETRIES = 1000;

  private final ScheduledExecutorService scheduledExecutorService;
  public BlockingDeque<UpscaleRequest> receivedRequests = new LinkedBlockingDeque<>();
  public BlockingDeque<Pause<UpscaleRequest>> retryUpscaleRequests = new LinkedBlockingDeque<>();
  public BlockingDeque<GithubActionJob> receivedRetryRequests = new LinkedBlockingDeque<>();
  private final Map<String, ActionPool> actionPoolMap;
  private final Optional<ActionPool> defaultActionPool;
  private final MetricsContainer metricsContainer;
  private Compute compute;
  private GithubService githubService;
  private ConfigReader configReader;
  private final ExecutorService processMessageThread;
  private final ExecutorService threadPerTasks;
  /**
   * Tracks whether a GitHub runner is currently busy. This helps determine when it is safe to
   * delete the instance after the runner reports it is no longer in use.
   */
  private final Cache<String, AtomicBoolean> runnerLastBusy;

  /**
   * Prevents accidental deletions caused by unreliable data from the GitHub API or compute APIs
   * (e.g., an occasional incorrect timestamp). Instead of deleting an instance immediately, we
   * increment a delete counter. Only after the counter reaches a threshold do we proceed with the
   * actual deletion.
   */
  private final Cache<DeleteInstanceRequest, AtomicInteger> instanceIdDeleteCounter;

  /**
   * Prevents accidental deletions caused by unreliable data from the GitHub API or compute APIs
   * (e.g., an occasional incorrect timestamp). Instead of deleting an instance immediately, we
   * increment a delete counter. Only after the counter reaches a threshold do we proceed with the
   * actual deletion.
   */
  private final Cache<RunnerNameId, AtomicInteger> runnerIdDeleteCounter;

  /**
   * Prevents redundant scaling events by tracking jobs that have already triggered an upscale. This
   * helps protect the system (especially from third-party triggers) and avoids unnecessary instance
   * creation.
   */
  private final Cache<String, AtomicInteger> githubRunnerIdUpscaledCache;

  /**
   * When a burst of jobs arrives, the underlying compute engine (AWS, GCP, etc.) may not
   * immediately report the total number of active instances. This race condition can temporarily
   * allow more than the configured maximum number of instances to be created.
   * <p>
   * To mitigate this, we use a short-lived cache that tracks the number of instances created per
   * action pool. The cache ensures that provisioning never exceeds the maximum allowed size of the
   * pool during its lifetime.
   * <p>
   * In plain terms: the cache acts as a safeguard so we never provision more instances than the
   * action pool’s maximum size.
   */
  private final Cache<String, AtomicInteger> actionPoolUpScale;

  public Autoscaler(Compute compute, GithubService githubService, ConfigReader configReader,
      MetricsContainer metricsContainer) throws InterruptedException {
    this.compute = compute;
    this.githubService = githubService;
    this.configReader = configReader;
    this.metricsContainer = metricsContainer;
    this.processMessageThread = Executors.newFixedThreadPool(5);
    this.scheduledExecutorService = Executors.newScheduledThreadPool(5);
    this.threadPerTasks = Executors.newCachedThreadPool();
    this.actionPoolMap = populateActionPoolMap();
    this.defaultActionPool = actionPoolMap.values().stream().filter(ActionPool::isDefault)
        .findAny();
    this.scheduledExecutorService.scheduleWithFixedDelay(this::handleComputeAndRunners, 1, 30,
        TimeUnit.SECONDS);
    this.scheduledExecutorService.scheduleWithFixedDelay(this::updateInstanceCountGauge, 1, 30,
        TimeUnit.SECONDS);
    this.processMessageThread.execute(this::processMessage);
    this.processMessageThread.execute(this::processRetryMessage);
    this.processMessageThread.execute(this::scheduleRetry);

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
    this.githubRunnerIdUpscaledCache = Caffeine.newBuilder()
        .maximumSize(1_000_000)
        .expireAfterWrite(Duration.ofHours(3))
        .build();
  }


  /**
   * @return map of action pool names to {@link ActionPool}
   */
  @VisibleForTesting
  private Map<String, ActionPool> populateActionPoolMap() {
    return this.getConfigReader().getActionPoolMap().values().stream().collect(
        Collectors.toMap(ActionPool::getName, Function.identity(), (a, b) -> b,
            ConcurrentHashMap::new));
  }

  private ConfigReader getConfigReader() {
    return configReader;
  }


  /**
   * Items placed into {@link Autoscaler#retryUpscaleRequests} are moved back to
   * {@link Autoscaler#receivedRequests} once their configured wait time has expired.
   */
  private void scheduleRetry() {
    while (true) {
      try {
        Thread.sleep(50);
        Pause<UpscaleRequest> upscaleRequestPause;
        List<Pause<UpscaleRequest>> addBack = new ArrayList<>();
        while ((upscaleRequestPause = this.retryUpscaleRequests.poll(1, TimeUnit.MINUTES))
            != null) {
          try {
            if (Instant.now().isAfter(upscaleRequestPause.instant())) {
              this.receivedRequests.offer(upscaleRequestPause.object());
            } else {
              addBack.add(upscaleRequestPause);
            }
          } catch (Exception e) {
            log.error("Some how there's an excpetion here", e);
          }
        }
        addBack.forEach(this.retryUpscaleRequests::offer);

      } catch (Exception e) {
        log.error("Rescheduling experienced an error", e);
      }
    }
  }

  /**
   * Adds the upscale requests to the  {@link Autoscaler#retryUpscaleRequests} dequeue.
   *
   * @param waitInMilliseconds - the time the upscale should wait before being retried
   * @param upscaleRequest     - the upscale request that should be retried later
   */
  private void retryLater(long waitInMilliseconds, UpscaleRequest upscaleRequest) {
    log.debug(
        "Will retry upscale for action pool %s".formatted(upscaleRequest.actionPool.getName()));
    if (upscaleRequest.upScaleReason == UpScaleReason.RETRY_POOL_FULL) {
      metricsContainer.instanceRetriesPoolFull(upscaleRequest.actionPool.getName());
    } else if (upscaleRequest.upScaleReason == UpScaleReason.RETRY_FAILED_CREATE) {
      metricsContainer.instanceRetriesFailedCreate(upscaleRequest.actionPool.getName());
    }
    retryUpscaleRequests.offer(
        new Pause<>(Instant.now().plusMillis(waitInMilliseconds), upscaleRequest));
  }

  /**
   * To limit the number of api calls to github api we run several processes together
   */
  private void handleComputeAndRunners() {
    try {
      List<Runner> runners = githubService.listRunnersInGroup();
      Map<String, Runner> runnersMap = runners.stream()
          .collect(Collectors.toMap(Runner::getName, Function.identity()));
      updateRunnerInfo(runners);
      scaleDownInstance(runnersMap);
      orphanedRunners(runners);
      deleteExpiredInstances();
      deleteExpiredRunners();
    } catch (Exception e) {
      log.error("Failed the main loop ", e);
    }
  }

  /**
   * For metrics
   */
  private void updateInstanceCountGauge() {
    this.compute.listAllComputeInstances().forEach((key, insts) ->
        metricsContainer.updateInstanceCount(key, insts.instances().size()));
  }

  /**
   * Updated the {@link Autoscaler#runnerLastBusy} cache with runner status
   *
   * @param runners - github runners
   */
  private void updateRunnerInfo(List<Runner> runners) {
    try {
      runners.stream().forEach(r -> {
        this.runnerLastBusy.get(r.getName() + "", key -> new AtomicBoolean(true));
      });
    } catch (Exception e) {
      log.error("error updating runner last busy due to: {}", e.getMessage());
    }
  }

  /**
   *
   */
  private void deleteExpiredInstances() {
    var map = new HashMap<>(instanceIdDeleteCounter.asMap());
    map.keySet().stream()
        .filter(key -> map.get(key).intValue() > Constants.DELETE_INSTANCE_RUNNER_THRESHOLD)
        .forEach(key -> {
          this.threadPerTasks.execute(() -> {
            try {
              if (this.compute.deleteCompute(key)) {
                metricsContainer.instanceDeletedTotal(key.getActionPool().getName(), true);
              } else {
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

  /**
   * Deletes expired runners once the {@link Constants#DELETE_INSTANCE_RUNNER_THRESHOLD} has been
   * exceeded. The threshold is managed through
   * {@link Autoscaler#incrementRunnerIdDeleteCounter(Runner)}, which tracks repeated delete
   * attempts to prevent accidental removals.
   */
  private void deleteExpiredRunners() {
    var map = new HashMap<>(runnerIdDeleteCounter.asMap());
    map.keySet().stream()
        .filter(key -> map.get(key).intValue() > Constants.DELETE_INSTANCE_RUNNER_THRESHOLD)
        .forEach(key -> {
          this.threadPerTasks.execute(() -> {
            if (this.githubService.deleteRunner(key.id())) {
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

  /**
   * Increments delete counter for compute instances.
   *
   * @param deleteInstanceRequest
   */
  private void incrementInstanceIdDeleteCounter(DeleteInstanceRequest deleteInstanceRequest) {
    this.instanceIdDeleteCounter.get(deleteInstanceRequest, (key) -> new AtomicInteger(1))
        .incrementAndGet();
  }

  /**
   * Increments delete counter for github runners
   *
   * @param runner
   */
  private void incrementRunnerIdDeleteCounter(Runner runner) {
    this.runnerIdDeleteCounter.get(new RunnerNameId(runner.getName(), runner.getId() + ""),
        (key) -> new AtomicInteger(1)).incrementAndGet();

  }


  /**
   * Consumes retry requests offered by {@link com.nimbusrun.actiontracker.RetryService}. For each
   * {@link GithubActionJob}, this method checks with the GitHub API to determine whether the job is
   * still queued. If so, the job is retried by passing it back to
   * {@link #receive(GithubActionJob)}.
   */
  private synchronized void processRetryMessage() {
    while (true) {
      try {
        GithubActionJob gj;
        while ((gj = this.receivedRetryRequests.poll(1, TimeUnit.MINUTES)) != null) {
          if (this.githubService.isJobQueued(gj.getRunUrl())) {
            log.info("Retrying payload {}", gj.getJsonStr());
            receive(gj);
          }
        }
      } catch (Exception e) {
      }
    }
  }

  /**
   * Core upscaler loop.
   * <p>
   * Consumes messages from {@link Autoscaler#receivedRequests}. If the target {@link ActionPool}
   * has capacity, attempts to provision a new instance. If the pool is full or provisioning fails,
   * the request is deferred to {@link Autoscaler#retryUpscaleRequests} for a later retry.
   * <p>
   * Duplicate protection: if a given workflow job has already triggered an upscale (tracked via
   * {@code githubRunnerIdUpscaledCache}), the request is ignored to prevent redundant scaling.
   */
  private synchronized void processMessage() {
    while (true) {
      try {

        UpscaleRequest upscaleRequest;
        while ((upscaleRequest = receivedRequests.poll(20, TimeUnit.SECONDS)) != null) {
          if (githubRunnerIdUpscaledCache.getIfPresent(upscaleRequest.getWorkflowJobId()) != null) {
            log.warn("Upscale event already happened for worflow job id: %s".formatted(
                upscaleRequest.getWorkflowJobId()));
            continue;
          }
          if (upscaleRequest.getRetryCreateFailed() > MAX_CREATE_FAILURE_RETRIES
              || upscaleRequest.getRetryPoolFull() > MAX_CREATE_POOL_FULL_RETRIES) {
            log.info(
                "Action pool %s not being expanded due to too many retries. pool full: %s and failed create: %s"
                    .formatted(upscaleRequest.actionPool.getName(),
                        upscaleRequest.getRetryPoolFull(), upscaleRequest.getRetryCreateFailed()));
            continue;
          }
          ActionPool pool = upscaleRequest.actionPool;
          AtomicInteger numberOfInstances = actionPoolUpScale.get(pool.getName(),
              (k) -> new AtomicInteger(0));
          int maxInstanceCount = pool.getMaxInstances().orElse(Constants.DEFAULT_MAX_INSTANCES);
          boolean hasUnlimitedInstances = maxInstanceCount == 0;
          ListInstanceResponse listInstanceResponse = compute.listComputeInstances(pool);

          if (!hasUnlimitedInstances && (maxInstanceCount <= listInstanceResponse.instances().size()
              || maxInstanceCount <= numberOfInstances.get())) {
            this.retryLater(5000,
                upscaleRequest.retryPoolFull()); // So it doesn't occupy the CPU full time
            continue;
          }
          ActionPool finalPool = pool;
          UpscaleRequest finalUpscaleRequest = upscaleRequest;
          numberOfInstances.incrementAndGet();

          this.threadPerTasks.execute(() -> {
            try {
              log.info("Attempting to make instance for action pool: {}", finalPool.getName());
              boolean successful = compute.createCompute(finalPool);
              if (successful) {
                metricsContainer.instanceCreatedTotal(finalPool.getName(), true);
                githubRunnerIdUpscaledCache.put(finalUpscaleRequest.getWorkflowJobId(),
                    new AtomicInteger(0));
              } else {
                metricsContainer.instanceCreatedTotal(finalPool.getName(), false);

                this.retryLater(5000, finalUpscaleRequest.retryCreateFailed());
              }
            } catch (InstanceCreateTimeoutException e) {
              metricsContainer.instanceCreatedTotal(finalPool.getName(),
                  e.isShouldHaveBeenCreated());
            } catch (Exception e) {
              metricsContainer.instanceCreatedTotal(finalPool.getName(), false);
              Utils.excessiveErrorLog(
                  "Failed to create compute instance for action pool %s due to %s".formatted(
                      finalPool.getName(), e.getMessage()), e, log);
            }
          });
        }
      } catch (Exception e) {

      }
    }
  }

  /**
   * Identifies and handles orphaned runners.
   * <p>
   * A runner is considered orphaned if its status is "offline". This typically occurs when the
   * underlying compute instance has been deleted or shut down, which is expected behavior once an
   * instance exceeds its maximum idle time. For each orphaned runner, the delete counter is
   * incremented to eventually trigger cleanup.
   *
   * @param runners list of GitHub runners to evaluate
   */
  private void orphanedRunners(List<Runner> runners) {
    runners.stream()
        .filter(r -> "offline".equalsIgnoreCase(r.getStatus()))
        .peek(r -> log.debug("Incrementing delete counter for orphan runner {}", r.getName()))
        .forEach(r -> incrementRunnerIdDeleteCounter(r));
  }

  /**
   * Evaluates and scales down compute instances when they are no longer needed.
   * <p>
   * An instance is considered eligible for scale-down if: 1. Its associated GitHub runner was
   * previously busy but is now deleted (i.e., the job finished and the runner was removed). 2. It
   * has exceeded its maximum idle time threshold.
   * <p>
   * For each eligible instance, the delete counter is incremented, which eventually triggers the
   * instance to be terminated.
   *
   * @param runners map of runner name → runner object, used to determine the current state of
   *                GitHub runners
   */
  private void scaleDownInstance(Map<String, Runner> runners) {

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
            if (this.runnerLastBusy.getIfPresent(instance.getInstanceName()) != null
                && runner == null) {
              runnerComplete = true;
            }
            Duration instanceUpDuration = Duration.between(
                Instant.ofEpochMilli(instance.getInstanceCreateTimeInMilli()), Instant.now());
            int idleTime = actionPool.getInstanceIdleScaleDownTimeInMinutes()
                .orElse(Constants.DEFAULT_INSTANCE_IDLE_TIME_IN_MINUTES);
            if (idleTime < instanceUpDuration.toMinutes()) {
              instanceIdleTimeExceeded = true;
            }
            if (runnerBusy != null && runnerBusy) {
              return;
            }
            if (Boolean.TRUE.equals(runnerComplete)) {
              log.debug(
                  "incrementing delete counter for instance id: {}, name: {} due to runner complete.",
                  instance.getInstanceId(), instance.getInstanceName());
              incrementInstanceIdDeleteCounter(
                  new DeleteInstanceRequest(actionPool, instance.getInstanceId(),
                      instance.getInstanceName(), instance.getExtraProperties()));
            } else if (instanceIdleTimeExceeded) {
              log.debug(
                  "incrementing delete counter for instance id: {}, name: {} due to idle time exceeded.",
                  instance.getInstanceId(), instance.getInstanceName());
              incrementInstanceIdDeleteCounter(
                  new DeleteInstanceRequest(actionPool, instance.getInstanceId(),
                      instance.getInstanceName(), instance.getExtraProperties()));
            }
          });
        } catch (Exception e) {
          log.error("Failed to evaluate scale down for action pool: %s, due to %s".formatted(
              actionPool.getName(), e.getMessage()), e);
        }
        return null;
      });

    });
    try {
      List<Future<String>> list = this.threadPerTasks.invokeAll(callables);
      for (var f : list) {
        f.get();
      }
    } catch (InterruptedException | ExecutionException e) {
      log.error("Failed to invoke scaled due to: {}", e.getMessage());
    }
  }


  /**
   * So not to block receiver the githubActionJob is offered to a dequeue for later processing in
   * method {@link Autoscaler#processRetryMessage()}
   *
   * @param githubActionJob
   */
  public void receiveRetry(GithubActionJob githubActionJob) {
    this.receivedRetryRequests.offer(githubActionJob);
  }

  /**
   * Determines whether the given {@link GithubActionJob} matches this autoscaler's configuration
   * and should trigger an upscale request.
   * <p>
   * The job is validated against:
   * <ul>
   *   <li>Workflow state (must still be queued).</li>
   *   <li>Runner group name.</li>
   *   <li>Action pool configuration and labels.</li>
   * </ul>
   * <p>
   * Invalid jobs (e.g., wrong labels, unknown action pool, or no pool available)
   * are logged and recorded in metrics, but not processed further.
   * <p>
   * If valid, the job is assigned to a matching action pool (or the default
   * action pool if available) and added to the {@code receivedRequests} queue
   * for scaling.
   *
   * @param gj the GitHub Actions job under evaluation
   * @return {@code true} if the job was accepted and queued for processing; {@code false} otherwise
   */
  public boolean receive(GithubActionJob gj) {

    ValidWorkFlowJob validWorkflowJob = new ValidWorkFlowJob(gj, actionPoolMap,
        this.githubService.getRunnerGroupName());
    if (validWorkflowJob.isWorkflowIsNotQueued()) {
      return false;
    }
    if (validWorkflowJob.isForGroup()) {
      ActionPool actionPool = null;

      if (validWorkflowJob.isInvalidLabels()) {
        this.metricsContainer.invalidWorkflowJobLabelTotal(gj.getRepositoryFullName(),
            gj.getWorkflowName(), gj.getName());
        log.warn("Repository: {}, workflow name: {}, workflow job: {} has invalids labels: {}",
            gj.getRepositoryFullName(), gj.getWorkflowName(), gj.getName(), gj.getInvalidLabels());
      }
      if (validWorkflowJob.isInvalidActionPool()) {
        metricsContainer.invalidActionPoolTotal(gj.getActionPoolName().get(),
            gj.getRepositoryFullName(), gj.getWorkflowName());
        List<String> actionPoolNames = this.actionPoolMap.values().stream().map(ActionPool::getName)
            .toList();
        log.warn(
            "Unknown action pool specified with name {} from repository: {}, workflow name: {}. Valid Action Pool names are: {}. Please let repository maintainer know to update workflow",
            gj.getActionPoolName().orElse("N/A"), gj.getRepositoryFullName(), gj.getWorkflowName(),
            actionPoolNames);
      } else if (gj.getActionPoolName().isPresent()) {
        actionPool = actionPoolMap.get(gj.getActionPoolName().get());
      } else {
        actionPool = defaultActionPool.orElse(null);
      }
      if (validWorkflowJob.isInvalidActionPool() || validWorkflowJob.isInvalidLabels()) {
        return false;
      }
      if (actionPool == null) {
        log.warn("No action pool specified for workflow and no default action pool configured: {}",
            gj.getJsonStr());
        return false;
      }
      log.info("Received action pool request for {} and runner group: {}, run_url: {}",
          actionPool.getName(), this.githubService.getRunnerGroupName(), gj.getHtmlUrl());

      return receivedRequests.add(new UpscaleRequest(actionPool, gj.getId()));
    }
    return false;
  }


  public enum UpScaleReason {
    NEW_REQUEST, RETRY_POOL_FULL, RETRY_FAILED_CREATE;
  }

  public record Pause<T>(Instant instant, T object) {


  }

  @PostConstruct
  public void redeliver() throws ProtocolException, GeneralSecurityException, IOException {
    if (!this.githubService.isReplayFailedDeliverOnStartup()
        || this.githubService.getWebhookId() == null) {
      return;
    }
    try {
      List<DeliveryRecord> records = this.githubService.listDeliveries();
      Map<String, List<DeliveryRecord>> map = records.stream()
          .collect(Collectors.groupingBy(d -> d.getGuid()));
      List<DeliveryRecord> redeliver = new ArrayList<>();
      map.forEach((guid, recordList) -> {
        recordList.stream().max(Comparator.comparing(DeliveryRecord::getDeliveredAt))
            .filter(d -> d.getStatusCode() >= 300)
            .ifPresent(redeliver::add);
      });
      for (DeliveryRecord del : redeliver) {
        this.githubService.reDeliveryFailures(del.getId());
      }
    } catch (Exception e) {
      Utils.excessiveErrorLog("Failed to run redeliver process for failed deliveries", e, log);
    }
  }

  public static class UpscaleRequest {

    @Getter
    private int retryPoolFull;
    @Getter
    private int retryCreateFailed;
    @Getter
    private UpScaleReason upScaleReason;
    @Getter
    private final String workflowJobId;
    private final ActionPool actionPool;

    public UpscaleRequest(ActionPool actionPool, String workflowJobId) {
      this.workflowJobId = workflowJobId;
      this.retryPoolFull = 0;
      this.retryCreateFailed = 0;
      this.actionPool = actionPool;
    }

    public UpscaleRequest retryPoolFull() {
      upScaleReason = UpScaleReason.RETRY_POOL_FULL;
      this.retryPoolFull += 1;
      return this;
    }

    public UpscaleRequest retryCreateFailed() {
      upScaleReason = UpScaleReason.RETRY_FAILED_CREATE;
      this.retryCreateFailed += 1;
      return this;
    }


  }

  public record RunnerNameId(String name, String id) {

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
      RunnerNameId that = (RunnerNameId) object;
      return Objects.equals(id, that.id) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, id);
    }
  }
}
