package com.nimbusrun.autoscaler.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class MetricsContainer {

  public static final String CREATE_OPERATION = "create";
  public static final String DELETE_OPERATION = "delete";
  public static final String POOL_NAME_TAG = "pool_name";
  public static final String REPOSITORY_NAME_TAG = "repository_name";
  public static final String WORKFLOW_NAME_TAG = "workflow_name";
  public static String INSTANCE_OPERATIONS_TOTAL = "instance_operations_total";
  public static String INSTANCE_COUNT = "instance_count";
  public static String INSTANCE_CREATE_RETRIES = "instance_create_retries_total";
  public static String INVALID_ACTION_POOL_TOTAL = "invalid_action_pool_total";
  public static String INVALID_WORKFLOW_JOB_LABEL_TOTAL = "invalid_workflow_job_label_total";
  public static String ACTION_POOL_PROCESS_TIME_TOTAL = "action_pool_process_time_total";
  /*TODO
      - counter user trigger a workflow_run
      - counter repository triggering workflow_run
   */
  private final MeterRegistry meterRegistry;
  private final Map<String, AtomicInteger> actionPoolGaugeMap = new ConcurrentHashMap<>();

  public MetricsContainer(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void updateInstanceCount(String actionPoolName, int instanceCount) {

    AtomicInteger mapAi = actionPoolGaugeMap.computeIfAbsent(actionPoolName, (key) -> {
      AtomicInteger ai = new AtomicInteger(instanceCount);
      Gauge.builder(INSTANCE_COUNT, ai::get).tag(POOL_NAME_TAG, actionPoolName)
          .register(this.meterRegistry);
      return ai;
    });
    mapAi.set(instanceCount);
  }

  public void instanceRetriesPoolFull(String actionPoolName) {
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of(POOL_NAME_TAG, actionPoolName));
    tags.add(Tag.of("type", "full"));
    Counter.builder(INSTANCE_CREATE_RETRIES).description("""
            Indicates that the action pool compute instance count hit its limit. The job will now retry periodically to trigger another upscale event when the action pool as available capacity
            """)
        .tags(tags).register(meterRegistry).increment();
  }

  public void instanceRetriesFailedCreate(String actionPoolName) {
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of(POOL_NAME_TAG, actionPoolName));
    tags.add(Tag.of("type", "failed"));
    Counter.builder(INSTANCE_CREATE_RETRIES).description("""
            Indicates that the action pool compute instance failed to upscale the action pool. This could indicate that something was misconfigured or missing required permissions to upscale. The job will now retry periodically to trigger another upscale event when the action pool as available capacity
            """)
        .tags(tags).register(meterRegistry).increment();
  }

  public void invalidActionPoolTotal(String invalidPoolName, String repositoryFullName,
      String workflowName) {
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of(POOL_NAME_TAG, invalidPoolName));
    tags.add(Tag.of(REPOSITORY_NAME_TAG, repositoryFullName));
    tags.add(Tag.of(WORKFLOW_NAME_TAG, workflowName));
    Counter.builder(INVALID_ACTION_POOL_TOTAL).description("""
        This is incremented when you have a valid action group but you specify an invalid action pool.
        """).tags(tags).register(meterRegistry).increment();
  }

  public void invalidWorkflowJobLabelTotal(String repositoryFullName, String workflowName,
      String workflowJobName) {
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of(REPOSITORY_NAME_TAG, repositoryFullName));
    tags.add(Tag.of(WORKFLOW_NAME_TAG, workflowName));
    tags.add(Tag.of("workflow_job_name", workflowJobName));
    Counter.builder(INVALID_WORKFLOW_JOB_LABEL_TOTAL).description("""
        If your workflow has an invalid label on the job. Additional labels on jobs will prevent self-hosted runners made by Nimbus to process them. This is a github actions design decision.
        """).tags(tags).register(meterRegistry).increment();
  }

  public void instanceCreatedTotal(String actionPoolName, boolean success) {
    instanceOperations(actionPoolName, success, CREATE_OPERATION);
  }

  public void instanceDeletedTotal(String actionPoolName, boolean success) {
    instanceOperations(actionPoolName, success, DELETE_OPERATION);
  }

  public void actionPoolProcessTime(String actionPoolName, long processTime){
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of(POOL_NAME_TAG, actionPoolName));
    Counter.builder(ACTION_POOL_PROCESS_TIME_TOTAL).description("""
        Tracks the total time per compute of an action pool has been up. This is useful for reporting in knowing which action pool is getting used the most and potential costs.
        """).tags(tags).register(meterRegistry).increment(processTime);
  }

  private void instanceOperations(String actionPoolName, boolean success, String operation) {
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of(POOL_NAME_TAG, actionPoolName));
    tags.add(Tag.of("type", operation));
    String successTag = "success";
    String failureTag = "failure";
    tags.add(Tag.of("result", success ? successTag : failureTag));
    Counter.builder(INSTANCE_OPERATIONS_TOTAL).description("""
            Increments when an operation to a compute instance occurs. type(%s/%s) and result(%s/%s)
            """.formatted(CREATE_OPERATION, DELETE_OPERATION, successTag, failureTag)).
        tags(tags).register(meterRegistry).increment();

  }


}
