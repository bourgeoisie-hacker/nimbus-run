package com.nimbusrun.autoscaler.autoscaler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusrun.autoscaler.github.GithubService;
import com.nimbusrun.autoscaler.github.orm.listDelivery.DeliveryRecord;
import com.nimbusrun.autoscaler.github.orm.runner.Runner;
import com.nimbusrun.autoscaler.metrics.MetricsContainer;
import com.nimbusrun.compute.ActionPool;
import com.nimbusrun.compute.Compute;
import com.nimbusrun.compute.ListInstanceResponse;
import com.nimbusrun.config.ConfigReader;
import com.nimbusrun.github.GithubActionJob;
import com.nimbusrun.github.WorkflowJobAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AutoscalerTest {

  @Mock
  private Compute compute;

  @Mock
  private GithubService githubService;

  @Mock
  private ConfigReader configReader;

  @Mock
  private MetricsContainer metricsContainer;

  @Mock
  private ActionPool actionPool;

  private Autoscaler autoscaler;

  @BeforeEach
  public void setUp() throws InterruptedException {
    // Setup mock behavior for ConfigReader
    Map<String, ActionPool> actionPoolMap = new HashMap<>();
    actionPoolMap.put("test-pool", actionPool);
    when(configReader.getActionPoolMap()).thenReturn(actionPoolMap);
    when(actionPool.getName()).thenReturn("test-pool");
    when(actionPool.isDefault()).thenReturn(true);

    // Create the autoscaler instance
    autoscaler = new Autoscaler(compute, githubService, configReader, metricsContainer);
  }

  @Test
  public void testReceive_withQueuedJobAndMatchingActionPool_shouldAddToQueue() {
    // Arrange
    GithubActionJob job = mock(GithubActionJob.class);
    when(job.getAction()).thenReturn(WorkflowJobAction.QUEUED);
    when(job.getActionGroupName()).thenReturn(Optional.of("test-group"));
    when(job.getActionPoolName()).thenReturn(Optional.of("test-pool"));
    when(job.getId()).thenReturn("job-123");
    when(githubService.getRunnerGroupName()).thenReturn("test-group");

    // Act
    boolean result = autoscaler.receive(job);

    // Assert
    assertTrue(result);
    // Verify that a request was added to the queue
    assertFalse(autoscaler.receivedRequests.isEmpty());
  }

  @Test
  public void testReceive_withNonQueuedJob_shouldReturnFalse() {
    // Arrange
    GithubActionJob job = mock(GithubActionJob.class);
    when(job.getAction()).thenReturn(WorkflowJobAction.COMPLETED);

    // Act
    boolean result = autoscaler.receive(job);

    // Assert
    assertFalse(result);
    // Verify that no request was added to the queue
    assertTrue(autoscaler.receivedRequests.isEmpty());
  }

  @Test
  public void testReceive_withMismatchedRunnerGroup_shouldReturnFalse() {
    // Arrange
    GithubActionJob job = mock(GithubActionJob.class);
    when(job.getAction()).thenReturn(WorkflowJobAction.QUEUED);
    when(job.getActionGroupName()).thenReturn(Optional.of("different-group"));
    when(githubService.getRunnerGroupName()).thenReturn("test-group");

    // Act
    boolean result = autoscaler.receive(job);

    // Assert
    assertFalse(result);
    // Verify that no request was added to the queue
    assertTrue(autoscaler.receivedRequests.isEmpty());
  }

  @Test
  public void testReceive_withMissingActionPool_shouldReturnFalse() {
    // Arrange
    GithubActionJob job = mock(GithubActionJob.class);
    when(job.getAction()).thenReturn(WorkflowJobAction.QUEUED);
    when(job.getActionGroupName()).thenReturn(Optional.of("test-group"));
    when(job.getActionPoolName()).thenReturn(Optional.of("non-existent-pool"));
    when(githubService.getRunnerGroupName()).thenReturn("test-group");

    // Override the setup to make defaultActionPool empty
    java.lang.reflect.Field field;
    try {
      field = Autoscaler.class.getDeclaredField("defaultActionPool");
      field.setAccessible(true);
      field.set(autoscaler, Optional.empty());
    } catch (Exception e) {
      fail("Failed to set defaultActionPool to empty: " + e.getMessage());
    }

    // Act
    boolean result = autoscaler.receive(job);

    // Assert
    assertFalse(result); // Should return false because there's no action pool and no default pool
  }

  @Test
  public void testReceiveRetry_shouldAddToRetryQueue() {
    // Arrange
    GithubActionJob job = mock(GithubActionJob.class);

    // Act
    autoscaler.receiveRetry(job);

    // Assert
    // Verify that the job was added to the retry queue
    assertFalse(autoscaler.receivedRetryRequests.isEmpty());
    try {
      assertEquals(job, autoscaler.receivedRetryRequests.take());
    } catch (InterruptedException e) {
      fail("Failed to take job from queue: " + e.getMessage());
    }
  }

  @Test
  public void testHandleComputeAndRunners_shouldUpdateRunnersAndScaleDown() throws Exception {
    // Arrange
    List<Runner> runners = new ArrayList<>();
    Runner runner = mock(Runner.class);
    when(runner.getName()).thenReturn("test-runner");
    runners.add(runner);

    when(githubService.listRunnersInGroup()).thenReturn(runners);

    // Create a proper mock for ListInstanceResponse
    ListInstanceResponse response = mock(ListInstanceResponse.class);
    List<ListInstanceResponse.Instance> instances = new ArrayList<>();
    when(response.instances()).thenReturn(instances);

    // Mock the listComputeInstances method which is actually called in scaleDownInstance
    when(compute.listComputeInstances(any(ActionPool.class))).thenReturn(response);

    // Use reflection to access private method
    java.lang.reflect.Method method = Autoscaler.class.getDeclaredMethod("handleComputeAndRunners");
    method.setAccessible(true);

    // Act
    method.invoke(autoscaler);

    // Assert
    verify(githubService).listRunnersInGroup();
    // Verify that the compute.listComputeInstances method was called with any ActionPool
    verify(compute).listComputeInstances(any(ActionPool.class));
  }

  @Test
  public void testUpdateInstanceCountGauge_shouldUpdateMetrics() throws Exception {
    // Arrange
    Map<String, ListInstanceResponse> instanceResponses = new HashMap<>();
    ListInstanceResponse response = mock(ListInstanceResponse.class);
    List<ListInstanceResponse.Instance> instances = new ArrayList<>();
    instances.add(new ListInstanceResponse.Instance("test-instance", "i-123", "test-instance-name",
        System.currentTimeMillis()));
    when(response.instances()).thenReturn(instances);
    instanceResponses.put("test-pool", response);

    when(compute.listAllComputeInstances()).thenReturn(instanceResponses);

    // Use reflection to access private method
    java.lang.reflect.Method method = Autoscaler.class.getDeclaredMethod(
        "updateInstanceCountGauge");
    method.setAccessible(true);

    // Act
    method.invoke(autoscaler);

    // Assert
    verify(compute).listAllComputeInstances();
    verify(metricsContainer).updateInstanceCount("test-pool", 1);
  }

  @Test
  public void testRedeliver_withReplayEnabled_shouldRedeliverFailedDeliveries() throws Exception {
    // Arrange
    when(githubService.isReplayFailedDeliverOnStartup()).thenReturn(true);
    when(githubService.getWebhookId()).thenReturn("webhook-123");

    // Create a delivery record with only the necessary mocks
    List<DeliveryRecord> deliveries = new ArrayList<>();
    DeliveryRecord failedDelivery = mock(DeliveryRecord.class);
    when(failedDelivery.getGuid()).thenReturn("guid-123");
    when(failedDelivery.getId()).thenReturn("delivery-123");
    when(failedDelivery.getStatusCode()).thenReturn(500); // Failed delivery
    // We don't need to mock getDeliveredAt since we only have one delivery record
    deliveries.add(failedDelivery);

    when(githubService.listDeliveries()).thenReturn(deliveries);

    // Act
    autoscaler.redeliver();

    // Assert
    verify(githubService).reDeliveryFailures("delivery-123");
  }

  @Test
  public void testRedeliver_withReplayDisabled_shouldNotRedeliver() throws Exception {
    // Arrange
    when(githubService.isReplayFailedDeliverOnStartup()).thenReturn(false);

    // Act
    autoscaler.redeliver();

    // Assert
    verify(githubService, never()).listDeliveries();
    verify(githubService, never()).reDeliveryFailures(anyString());
  }

  @Test
  public void testPopulateActionPoolMap_shouldReturnCorrectMap() {
    // Arrange
    Map<String, ActionPool> configActionPools = new HashMap<>();
    ActionPool pool1 = mock(ActionPool.class);
    when(pool1.getName()).thenReturn("pool1");
    configActionPools.put("pool1", pool1);

    when(configReader.getActionPoolMap()).thenReturn(configActionPools);

    // Use reflection to access private method
    java.lang.reflect.Method method;
    try {
      method = Autoscaler.class.getDeclaredMethod("populateActionPoolMap");
      method.setAccessible(true);

      // Act
      @SuppressWarnings("unchecked")
      Map<String, ActionPool> result = (Map<String, ActionPool>) method.invoke(autoscaler);

      // Assert
      assertEquals(1, result.size());
      assertTrue(result.containsKey("pool1"));
      assertEquals(pool1, result.get("pool1"));
    } catch (Exception e) {
      fail("Failed to invoke populateActionPoolMap: " + e.getMessage());
    }
  }

  @Test
  public void testUpscaleRequest_retryPoolFull_shouldIncrementCounter() {
    // Arrange
    Autoscaler.UpscaleRequest request = new Autoscaler.UpscaleRequest(actionPool, "job-123");

    // Act & Assert
    assertEquals(0, request.getRetryPoolFull());
    request.retryPoolFull();
    assertEquals(1, request.getRetryPoolFull());
    assertEquals(Autoscaler.UpScaleReason.RETRY_POOL_FULL, request.getUpScaleReason());
  }

  @Test
  public void testUpscaleRequest_retryCreateFailed_shouldIncrementCounter() {
    // Arrange
    Autoscaler.UpscaleRequest request = new Autoscaler.UpscaleRequest(actionPool, "job-123");

    // Act & Assert
    assertEquals(0, request.getRetryCreateFailed());
    request.retryCreateFailed();
    assertEquals(1, request.getRetryCreateFailed());

    // This assertion now passes after fixing the bug in the implementation
    assertEquals(Autoscaler.UpScaleReason.RETRY_FAILED_CREATE, request.getUpScaleReason());
  }
}
