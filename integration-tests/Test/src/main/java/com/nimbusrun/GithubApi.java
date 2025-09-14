package com.nimbusrun;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.json.JSONObject;

@Slf4j
public class GithubApi {
  private String token;
  private String orgName;
  public GithubApi(String token, String orgName){
    this.token = token;
    this.orgName = orgName;
  }

  public boolean createRunnerGroup(String name){
    try (var client = createHttpClient()) {
      JSONObject obj = new JSONObject();
      obj.put("name", name);
      obj.put("visibility", "all");
//      obj.put("allows_public_repositories", false);
//      obj.put("allows_public_repositories", "false");


      var request = new HttpPost(
          String.format("https://api.github.com/orgs/%s/actions/runner-groups".formatted(orgName)));
      StringEntity entity = new StringEntity(obj.toString(), ContentType.APPLICATION_JSON);
      request.setEntity(entity);
      try (var response = client.execute(request)) {
        var body = new String(response.getEntity().getContent().readAllBytes());
        if ((response.getCode() >= 200 && response.getCode() < 300) || (response.getCode() == 409 && body.toLowerCase().contains("already exists"))) {

          return true;
        }
      }
    } catch (Exception e) {
      log.error("Failed to create runner group", e);
    }
    return false;
  }

  public boolean deleteRunnerGroup(String id){
    try (var client = createHttpClient()) {
      var request = new HttpDelete(
          String.format("https://api.github.com/orgs/%s/actions/runner-groups/%s".formatted(orgName, id)));
      try (var response = client.execute(request)) {
        if (response.getCode() >= 200 && response.getCode() < 300) {
          return true;
        }
      }
    } catch (Exception e) {
      log.error("Failed to create runner group", e);
    }
    return false;
  }
  private CloseableHttpClient createHttpClient() throws GeneralSecurityException {
    var config = RequestConfig.custom()
        .setConnectionRequestTimeout(5, TimeUnit.SECONDS)
        .setResponseTimeout(5, TimeUnit.SECONDS)
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
        .setDefaultHeaders(List.of(
            new BasicHeader("Authorization", "Bearer " + token),
            new BasicHeader("Accept", "application/vnd.github+json"),
            new BasicHeader("X-GitHub-Api-Version", "2022-11-28")
        ))
        .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
            .setSSLSocketFactory(sslSocketFactory)
            .build())
        .build();
  }
}
