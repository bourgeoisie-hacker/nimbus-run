package com.nimbusrun.autoscaler.autoscaler;

import com.nimbusrun.autoscaler.metrics.MetricsContainer;
import com.nimbusrun.compute.ListInstanceResponse.Instance;
import com.nimbusrun.config.ConfigReader;
import com.nimbusrun.github.GithubActionJob;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.yaml.snakeyaml.Yaml;

@ExtendWith(SpringExtension.class)
public class AutoScalerTest {
  public static final String LABEL_TEMPLATE = "${NIMBUS_LABELS}";
  public static final String JOB_ID_TEMPLATE = "${JOB_ID}";
  public static final String RUNNER_GROUP = "prod";
  public static final String ACTION_POOL_1 = "one";
  public static final String ACTION_POOL_3 = "three";

//  @Value("classpath:configs/config.yaml")
  String nimbusRunConfig = """
      ---
      actionPools:
        - name: one
          maxInstances: 1
          instanceIdleScaleDownTimeInMinutes: 1
          isDefault: true
        - name: two
          maxInstances: 1
          instanceIdleScaleDownTimeInMinutes: 1
          isDefault: false
        - name: three
          maxInstances: 2
          instanceIdleScaleDownTimeInMinutes: 1
          isDefault: false
      """;

  @Value("classpath:workflow_jobs/queued_good_workflow_job.json")
  Resource queuedGoodWorkflowJob;

  public AutoScalerTest() throws Exception {

  }


  public Beans createAutoScaler() throws InterruptedException, IOException {
    Yaml yaml = new Yaml();
    ComputeMock computeMock = new ComputeMock();
    computeMock.receiveComputeConfigs(yaml.load(nimbusRunConfig), "BLAH");
    ConfigReader configReader = Mockito.mock(ConfigReader.class);
    Mockito.when(configReader.getActionPoolMap()).thenReturn(computeMock.getActionPoolMap());
    GithubApiMock githubApiMock = new GithubApiMock(RUNNER_GROUP, computeMock);
    Autoscaler autoscaler =  new Autoscaler(computeMock, githubApiMock , configReader, new MetricsContainer(new PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT)), 5);
    return new Beans(autoscaler, computeMock, githubApiMock);
  }

  public String createLabels(Map<String, String> map){
    return map.keySet().stream().map(k-> "\"%s=%s\"".formatted(k, map.get(k))).collect(Collectors.joining(",\n"));
  }

  @Test
  public void testHappyPath() throws IOException, InterruptedException {
    Beans beans = createAutoScaler();
    Autoscaler autoscaler = beans.autoscaler();
    ComputeMock mock = beans.computeMock();
    GithubApiMock githubApiMock = beans.githubApiMock();
    String labels = createLabels(Map.of("action-group", RUNNER_GROUP, "action-pool", ACTION_POOL_1));
    GithubActionJob job = GithubActionJob.fromJson(queuedGoodWorkflowJob.getContentAsString(Charset.defaultCharset())
        .replace(LABEL_TEMPLATE, labels)
        .replace(JOB_ID_TEMPLATE, "1"));
    autoscaler.receive(job);
    Thread.sleep(20);
    Map<String, Instance> instances = mock.getInstanceMap()
        .get(job.getActionPoolName().get()).getInstanceMap();

    Assertions.assertEquals(1,
        mock.getInstanceMap().get(job.getActionPoolName().get()).getInstanceMap().size());
    Thread.sleep(1000);
    githubApiMock.addDeletedRunner(instances.values().stream().findFirst().get().getInstanceId());
    Thread.sleep(100);

    Assertions.assertEquals(0,
        mock.getInstanceMap().get(job.getActionPoolName().get()).getInstanceMap().size());
  }
  @Test
  public void maxInstances() throws IOException, InterruptedException {
    Beans beans = createAutoScaler();
    Autoscaler autoscaler = beans.autoscaler();
    ComputeMock mock = beans.computeMock();
    GithubApiMock githubApiMock = beans.githubApiMock();
    String labels = createLabels(Map.of("action-group", RUNNER_GROUP, "action-pool", ACTION_POOL_3));
    GithubActionJob job1 = GithubActionJob.fromJson(queuedGoodWorkflowJob.getContentAsString(Charset.defaultCharset())
        .replace(LABEL_TEMPLATE, labels)
        .replace(JOB_ID_TEMPLATE, "1"));
    GithubActionJob job2 = GithubActionJob.fromJson(queuedGoodWorkflowJob.getContentAsString(Charset.defaultCharset())
        .replace(LABEL_TEMPLATE, labels)
        .replace(JOB_ID_TEMPLATE, "2"));
    GithubActionJob job3 = GithubActionJob.fromJson(queuedGoodWorkflowJob.getContentAsString(Charset.defaultCharset())
        .replace(LABEL_TEMPLATE, labels)
        .replace(JOB_ID_TEMPLATE, "3"));
    autoscaler.receive(job1);
    autoscaler.receive(job2);
    autoscaler.receive(job3);
    Thread.sleep(20);
    Map<String, Instance> instances = mock.getInstanceMap()
        .get(ACTION_POOL_3).getInstanceMap();

    Assertions.assertEquals(2,
        mock.getInstanceMap().get(ACTION_POOL_3).getInstanceMap().size());

  }

  @Test
  public void incorrectActionPoolLabels() throws IOException, InterruptedException {
    Beans beans = createAutoScaler();
    Autoscaler autoscaler = beans.autoscaler();
    ComputeMock mock = beans.computeMock();
    GithubApiMock githubApiMock = beans.githubApiMock();
    {
      String labels1 = createLabels(
          Map.of("action-group", RUNNER_GROUP, "action-pool", "superrandomNoExist.wWhatGroup"));
      GithubActionJob job1 = GithubActionJob.fromJson(
          queuedGoodWorkflowJob.getContentAsString(Charset.defaultCharset())
              .replace(LABEL_TEMPLATE, labels1)
              .replace(JOB_ID_TEMPLATE, "1"));

      autoscaler.receive(job1);
      Thread.sleep(20);
      List<Instance> instances1 = mock.getInstanceMap().values().stream()
          .flatMap(i -> i.getInstanceMap().values().stream()).toList();
      Assertions.assertEquals(0,
          instances1.size());
    }
    {
      String labels2 = createLabels(
          Map.of("action-group", "somerandomgroup222@4v", "action-pool", "one"));
      GithubActionJob job2 = GithubActionJob.fromJson(
          queuedGoodWorkflowJob.getContentAsString(Charset.defaultCharset())
              .replace(LABEL_TEMPLATE, labels2)
              .replace(JOB_ID_TEMPLATE, "1"));
      autoscaler.receive(job2);
      Thread.sleep(20);
      List<Instance> instances2 = mock.getInstanceMap().values().stream()
          .flatMap(i -> i.getInstanceMap().values().stream()).toList();
      Assertions.assertEquals(0,
          instances2.size());
    }
  }

  @Test
  public void badLabels() throws IOException, InterruptedException {
    Beans beans = createAutoScaler();
    Autoscaler autoscaler = beans.autoscaler();
    ComputeMock mock = beans.computeMock();
    GithubApiMock githubApiMock = beans.githubApiMock();

    String labels = createLabels(Map.of("action-group", RUNNER_GROUP, "action-pool", ACTION_POOL_1, "preferred-os", "windows"));

    GithubActionJob job = GithubActionJob.fromJson(
          queuedGoodWorkflowJob.getContentAsString(Charset.defaultCharset())
              .replace(LABEL_TEMPLATE, labels)
              .replace(JOB_ID_TEMPLATE, "1"));

      autoscaler.receive(job);
      Thread.sleep(20);
      List<Instance> instances = mock.getInstanceMap().values().stream()
          .flatMap(i -> i.getInstanceMap().values().stream()).toList();
      Assertions.assertEquals(0, instances.size());

  }

}

record Beans(Autoscaler autoscaler, ComputeMock computeMock, GithubApiMock githubApiMock){}