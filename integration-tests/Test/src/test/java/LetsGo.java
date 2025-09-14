import com.nimbusrun.config.Config;
import com.nimbusrun.setup.SetupResources;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;

public class LetsGo {

  SetupResources setupResources;

  public LetsGo() throws IOException, InterruptedException {
    Files.walk(Paths.get(".")).forEach(System.out::println);
    this.setupResources = new SetupResources(new Config());

  }

  @AfterSuite
  public void cleanupAfterAllTests() {
    setupResources.stopAll();
  }

  @Test()
  public void testOs() throws IOException, InterruptedException, GeneralSecurityException {
    startUp("aws_test-os");

    try(CloseableHttpClient client = createHttpClient();){
      client
    }
    Assert.assertEquals(1,1);
  }

  public boolean startUp(String workflowName) throws InterruptedException, IOException {
    boolean startUp = setupResources.startUp(workflowName);
    if(!startUp){
      Assert.fail("Failed to startup workflow " + workflowName);
    }
    int checkCount =0;
    do{
      Thread.sleep(5000);
      checkCount++;
      if(checkCount > 4){
        Assert.fail("Failed to startUp in time workflow " + workflowName);
      } else if(!setupResources.isWorkflowAlive(workflowName)){
        Assert.fail("Process died for workflow " + workflowName);
      }

    }while (!setupResources.isNimbusRunReadyForTraffic(workflowName));
    return false;
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
