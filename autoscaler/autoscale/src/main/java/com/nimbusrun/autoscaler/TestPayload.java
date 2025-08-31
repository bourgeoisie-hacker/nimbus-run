package com.nimbusrun.autoscaler;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TestPayload {
    public static void main(String[] args) throws IOException {
        sendPayload("alh");
    }

    public static void sendPayload(String filePath) throws IOException {
        String payload = Files.readString(Paths.get("webhook-examples-mine/retry.json"));
//        String payload = Files.readString(Paths.get(filePath));
        try(var client = createHttpClient()){
            org.apache.hc.core5.http.io.entity.StringEntity entity = new StringEntity(payload);
            var post = new HttpPost("http://localhost:8080/webhook");
            post.setEntity(entity);
            var response =client.execute(post);
            System.out.println(response.getCode());
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }

    }


    private static CloseableHttpClient createHttpClient() throws GeneralSecurityException {
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
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(sslSocketFactory)
                        .build())
                .build();
    }
}
