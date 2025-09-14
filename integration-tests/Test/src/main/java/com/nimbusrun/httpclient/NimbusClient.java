package com.nimbusrun.httpclient;

import com.nimbusrun.orm.aws.AwsActionPoolsConfig;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;

public class NimbusClient {
  public static final String API_TEMPLATE="http://localhost:%s%s";

  public static final String POSTED_WEBHOOKS = "/api/v1/admin/posted-webhooks";
  public static final String CURRENT_INSTANCES = "/api/v1/admin/current-instances";
  public static final String ACTION_POOL_STATUS = "/api/v1/admin/action-pool-status";


  public AwsActionPoolsConfig actionPoolStatus(int port) throws GeneralSecurityException {
    try(CloseableHttpClient client=createHttpClient()){
      HttpGet request = new HttpGet(API_TEMPLATE.formatted(port, ACTION_POOL_STATUS));
      client.execute(request).getEntity().getContent();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
