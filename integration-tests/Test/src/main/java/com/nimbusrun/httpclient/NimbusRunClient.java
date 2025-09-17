package com.nimbusrun.httpclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusrun.orm.PostedWebhook;
import com.nimbusrun.orm.aws.AwsActionPoolsConfig;
import com.nimbusrun.orm.CurrentInstances;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;

@Slf4j
public class NimbusRunClient {
  public static void main(String[] args) throws GeneralSecurityException {

    AwsActionPoolsConfig pc = new NimbusRunClient().awsActionPoolStatus(8080);
    CurrentInstances ci = new NimbusRunClient().currentInstances(8080);
    List<PostedWebhook> ph = new NimbusRunClient().postedWebhooks(8080);
    System.out.println();
  }
  public static final String API_TEMPLATE="http://localhost:%s%s";// localhost because this will be running on the same server.

  public static final String POSTED_WEBHOOKS = "/api/v1/admin/posted-webhooks";
  public static final String CURRENT_INSTANCES = "/api/v1/admin/current-instances";
  public static final String ACTION_POOL_STATUS = "/api/v1/admin/action-pool-status";
  public static final ObjectMapper OM = new ObjectMapper();;

  public AwsActionPoolsConfig awsActionPoolStatus(int port) {
    try(CloseableHttpClient client=createHttpClient()){
      HttpGet request = new HttpGet(API_TEMPLATE.formatted(port, ACTION_POOL_STATUS));
      CloseableHttpResponse response = client.execute(
          request);
      String content = new String(response.getEntity().getContent().readAllBytes());;
      if(responseCodeIsPositive(response.getCode())){
        return OM.readValue(content,AwsActionPoolsConfig.class);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  public CurrentInstances currentInstances(int port)  {
    try(CloseableHttpClient client=createHttpClient()){
      HttpGet request = new HttpGet(API_TEMPLATE.formatted(port, CURRENT_INSTANCES));
      CloseableHttpResponse response = client.execute(
          request);
      String content = new String(response.getEntity().getContent().readAllBytes());;
      if(responseCodeIsPositive(response.getCode())){
        return OM.readValue(content, CurrentInstances.class);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  public List<PostedWebhook> postedWebhooks(int port)  {
    try(CloseableHttpClient client=createHttpClient()){
      HttpGet request = new HttpGet(API_TEMPLATE.formatted(port, POSTED_WEBHOOKS));
      CloseableHttpResponse response = client.execute(
          request);
      String content = new String(response.getEntity().getContent().readAllBytes());;
      if(responseCodeIsPositive(response.getCode())){
        return OM.readValue(content,  new com.fasterxml.jackson.core.type.TypeReference<List<PostedWebhook>>() {});
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  private CloseableHttpClient createHttpClient() {
    var config = RequestConfig.custom()
        .setConnectionRequestTimeout(1, TimeUnit.SECONDS)
        .setResponseTimeout(1, TimeUnit.SECONDS)
        .build();

    SSLContext sslContext = null;
    try {
      sslContext = SSLContextBuilder.create()
          .loadTrustMaterial(TrustAllStrategy.INSTANCE)
          .build();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    } catch (KeyManagementException e) {
      throw new RuntimeException(e);
    } catch (KeyStoreException e) {
      throw new RuntimeException(e);
    }

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
  public boolean responseCodeIsPositive(int code){
    return code >=200 && code < 300;
  }
}
