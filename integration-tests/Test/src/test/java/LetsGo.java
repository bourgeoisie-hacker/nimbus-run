import com.nimbusrun.config.Config;
import com.nimbusrun.httpclient.NimbusRunClient;
import com.nimbusrun.orm.CurrentInstances;
import com.nimbusrun.orm.PostedWebhook;
import com.nimbusrun.orm.aws.AwsActionPoolsConfig;
import com.nimbusrun.orm.aws.AwsActionPoolsConfig.ActionPool;
import com.nimbusrun.setup.SetupResources;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.Instance;

public class LetsGo {

  SetupResources setupResources;

  public LetsGo() throws IOException, InterruptedException {
//    Files.walk(Paths.get(".")).forEach(System.out::println);
    this.setupResources = new SetupResources(new Config());

  }

  @AfterSuite
  public void cleanupAfterAllTests() {
    setupResources.stopAll();
  }

  @Test()
  public void testOs() throws IOException, InterruptedException, GeneralSecurityException {
    String workflow = "aws_test-os";
    int port = startUp(workflow);
    setupResources.triggerWorkflow(workflow);

    //Check Posted webhooks
    //Check Get current instances
    //Check instances for correct settings

    NimbusRunClient runClient = new NimbusRunClient();
    List<PostedWebhook> postedWebhooks = waitForResponse(() -> runClient.postedWebhooks(port),
        (webhooks) -> webhooks.size() == 7, 5, Duration.ofSeconds(5));
    CurrentInstances currentInstances = waitForResponse(() -> runClient.currentInstances(port),
        (ci) -> ci.getPools().values().size() == 7, 5, Duration.ofSeconds(5));
    AwsActionPoolsConfig actionPools = runClient.awsActionPoolStatus(port);
    runClient.
    runClient.postedWebhooks(port);
    Assert.assertEquals(1, 1);
  }

  public void ensureInstancesAreCorrect(CurrentInstances currentInstances,
      AwsActionPoolsConfig actionPoolsConfig,
      Map<String, Integer> actionPoolCount) {
    List<String> errors = new ArrayList<>();
    //checking current instance count is correct
    currentInstances.getPools().forEach((poolName, instanceIds) -> {
      if (actionPoolCount.get(poolName) == null && !instanceIds.isEmpty()) {
        errors.add(
            "Action Pool %s has unexpected instances. Expected: 0, Actual: %s".formatted(poolName,
                instanceIds.size()));
      } else if (actionPoolCount.getOrDefault(poolName, 0) != instanceIds.size()) {
        errors.add(
            "Action Pool %s has unexpected number of instances. Expected: %s, Actual: %s".formatted(
                poolName, actionPoolCount.get(poolName), instanceIds.size()));
      }
    });
    if(!errors.isEmpty()){
      Assert.fail("Failed for reasons: "+ String.join(" | ",errors));
    }

    //finding instances and making sure those are correct
    actionPoolsConfig.getActionPool().forEach((actionPoolName, actionPool) -> {
      List<String> instanceIds = currentInstances.getPools().get(actionPoolName);
      Ec2Client ec2Client = Ec2Client.builder().region(Region.of(actionPool.getRegion())).build();

      for(String instanceId : instanceIds){
        Optional<Instance> instance = getInstanceById(ec2Client, instanceId);
        if(instance.isEmpty()){
          errors.add("Action Pool %s missing instance id: %s".formatted( actionPoolName, instanceId));
        }
        testInstance(instance.get(), actionPool, errors);
      }
    });
  }

  public void testInstance(Instance instance, ActionPool actionPool, List<String> errors){
    assertEqual(actionPool.getSubnet(),instance.subnetId(),"subnetId", errors );
    assertEqual(actionPool.getInstanceType(),instance.instanceType().toString(),"instanceType", errors );
    assertEqual(actionPool.getSecurityGroup(),instance.securityGroups().toString(),"", errors );
  }

  public void assertEqual(Object expected, Object actual, String type, List<String> errors){
    if(!expected.equals(actual)){
      errors.add("Type: %s expected: %s not match actual: %s ".formatted(type, expected, actual));
    }
  }
  public static Optional<Instance> getInstanceById(Ec2Client ec2, String instanceId) {
    try {
      // Using the paginator in case the account has many reservations
      var paginator = ec2.describeInstancesPaginator(
          DescribeInstancesRequest.builder()
              .instanceIds(instanceId) // You can also use a Filter: name("instance-id").values(instanceId)
              .build()
      );

      return paginator.reservations().stream()
          .flatMap(res -> res.instances().stream())
          .filter(i -> instanceId.equals(i.instanceId()))
          .findFirst();

    } catch (Ec2Exception e) {
      // If the instance ID is malformed or permissions are missing, you'll land here
      throw e;
    }
  }


  //long name
  public <T> T waitForResponse(
      Supplier<T> postedWebhookSup, Predicate<T> predicate, int maxCycles,
      Duration waitBetweenCycle) {
    int cycle = 0;
    T webhooks = postedWebhookSup.get();
    do {
      if (predicate.test(webhooks)) {
        return webhooks;
      }
      try {
        Thread.sleep(waitBetweenCycle.toSeconds());
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      cycle++;
    } while (cycle < maxCycles);
    Assert.fail("All webhooks weren't received");
    return null;// never gonna get here
  }

  public int startUp(String workflowName) throws InterruptedException, IOException {
    Optional<Integer> port = setupResources.startUp(workflowName);
    if (port.isEmpty()) {
      Assert.fail("Failed to startup workflow " + workflowName);
    }
    int checkCount = 0;
    do {
      Thread.sleep(5000);
      checkCount++;
      if (checkCount > 4) {
        Assert.fail("Failed to startUp in time workflow " + workflowName);
      } else if (!setupResources.isWorkflowAlive(workflowName)) {
        Assert.fail("Process died for workflow " + workflowName);
      }

    } while (!setupResources.isNimbusRunReadyForTraffic(workflowName));
    return port.get();
  }

  private CloseableHttpClient createHttpClient() throws GeneralSecurityException {
    var config = RequestConfig.custom()
        .setConnectionRequestTimeout(1, TimeUnit.SECONDS)
        .setResponseTimeout(1, TimeUnit.SECONDS)
        .build();

    var sslContext = SSLContextBuilder.create()
        .loadTrustMaterial(TrustAllStrategy.INSTANCE)
        .build();

    var sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
        .setSslContext(sslContext)
        .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
        .build();

    return HttpClients.custom()
        .setDefaultRequestConfig(config)
        .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
            .setSSLSocketFactory(sslSocketFactory)
            .build())
        .build();
  }

}
