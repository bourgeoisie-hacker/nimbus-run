
package com.nimbusrun.autoscaler.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.nimbusrun.autoscaler.github.orm.listDelivery.DeliveryRecord;
import com.nimbusrun.autoscaler.github.orm.runner.ListSelfHostedRunners;
import com.nimbusrun.autoscaler.github.orm.runner.Runner;
import com.nimbusrun.autoscaler.github.orm.runnergroup.ListRunnerGroup;
import com.nimbusrun.autoscaler.github.orm.runnergroup.RunnerGroup;
import com.nimbusrun.compute.Constants;
import com.nimbusrun.compute.GithubApi;
import com.nimbusrun.Utils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.*;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.*;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Component
public class GithubService implements GithubApi {

    private final String token;
    @Getter
    private final String organization;
    @Getter
    private final Integer runnerGroupId;
    @Getter
    private final String runnerGroupName;

    @Getter
    private final String webhookId;
    @Getter
    private final boolean replayFailedDeliverOnStartup;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OBJECT_MAPPER.findAndRegisterModules();
    }


    public GithubService(@Value("${github.token}") String token,
                         @Value("${github.organizationName}") String organization,
                         @Value("${github.groupName}") String runnerGroupName,
                         @Value("${github.webhookId:#{null}}") String webhookId,
                         @Value("${github.replayFailedDeliverOnStartup:#{false}}") boolean replayFailedDeliverOnStartup) {
        this.token = token;
        this.organization = organization;
        this.runnerGroupName = runnerGroupName;
        this.runnerGroupId = resolveRunnerGroupId(runnerGroupName);
        this.webhookId = webhookId;
        this.replayFailedDeliverOnStartup = replayFailedDeliverOnStartup;
    }

    private Integer resolveRunnerGroupId(String groupName) {
        return fetchRunnerGroups().stream()
                .flatMap(wrapper -> wrapper.getRunnerGroups().stream())
                .filter(group -> group.getName().equals(groupName))
                .map(RunnerGroup::getId)
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Runner group not found: {}", groupName);
                    return new IllegalStateException("Runner group not found: " + groupName);
                });
    }

    public List<ListRunnerGroup> fetchRunnerGroups() {
        try {
            return fetchPaginatedData(
                    new HttpGet(String.format("https://api.github.com/orgs/%s/actions/runner-groups", organization)),
                    new TypeReference<ListRunnerGroup>(){});
        } catch (Exception e) {
            log.error("Unable to fetch runner groups", e);
            return Collections.emptyList();
        }
    }

    public Optional<String> generateRunnerToken() {
        try (var client = createHttpClient()) {
            var request = new HttpPost(String.format("https://api.github.com/orgs/%s/actions/runners/registration-token", organization));
            try (var response = client.execute(request)) {
                if (response.getCode() >= 200 && response.getCode() < 300) {
                    var body = new String(response.getEntity().getContent().readAllBytes());
                    return Optional.of(new JSONObject(body).getString("token"));
                }
            }
        } catch (Exception e) {
            log.error("Failed to generate runner token", e);
        }
        return Optional.empty();
    }

    @Override
    public boolean isJobQueued(String runUrl) {
        try (var client = createHttpClient()) {
            var request = new HttpGet(runUrl);
            CloseableHttpResponse response = client.execute(request);
            String body = new String(response.getEntity().getContent().readAllBytes());
            if(response.getCode() >= 200 && response.getCode() < 300){
                JSONObject obj = new JSONObject(body);
                if(obj.has("status") && obj.getString("status").equalsIgnoreCase("queued")){
                    return true;
                }else {
                    return false;
                }
            }else {
                log.error("Error fetching job info for %s. Due to: %s".formatted(runUrl, response.getEntity().getContent(),body));
                return false;
            }
        }catch (Exception e){
            Utils.excessiveErrorLog("Error fetching job info for %s".formatted(runUrl), e, log);
        }
        return false;
    }

    public List<Runner> listRunnersInGroup() {
        return listRunnersInGroup(runnerGroupId.toString());
    }

    public List<Runner> listRunnersInGroup(String groupId) {
        try {
            var request = new HttpGet(String.format("https://api.github.com/orgs/%s/actions/runner-groups/%s/runners", organization, groupId));
            var pages = fetchPaginatedData(request, new TypeReference<ListSelfHostedRunners>(){});
            List<Runner> allRunners = new ArrayList<>();
            pages.forEach(page -> allRunners.addAll(page.getRunners()));
            return allRunners.stream().filter(this::runnerHaveCorrectActionGroupLabel).toList();
        } catch (Exception e) {
            log.error("Error listing runners for group {}", groupId, e);
            throw new RuntimeException(e);
        }
    }
    private boolean runnerHaveCorrectActionGroupLabel(Runner r) {
        return r.getLabels().stream().anyMatch(label -> {
            if (label.getName() != null) {
                String[] parts = label.getName().trim().split("=");
                if (parts.length == 2
                        && Constants.ACTION_GROUP_LABEL_KEY.equals(parts[0])
                        && this.runnerGroupName.equals(parts[1])) {
                    return true;
                }
            }
            return false;

        });
    }

    public boolean deleteRunner(String runnerId) {
        HttpDelete httpDelete = new HttpDelete("https://api.github.com/orgs/%s/actions/runners/%s".formatted(this.organization, runnerId));
        try(var client = createHttpClient(); var response = client.execute(httpDelete)){
            if(response.getCode() >= 200 && 300 > response.getCode()){
                return true;
            }
        }catch (Exception e ){
            String msg = "Failed to delete Runner";
            log.error(msg, e);
        }
        return false;
    }

    public List<DeliveryRecord> listDeliveries() throws ProtocolException, GeneralSecurityException, IOException {
        HttpGet httpGet = new HttpGet("https://api.github.com/orgs/%s/hooks/%s/deliveries".formatted(this.organization, this.webhookId));
         var dd =  fetchPaginatedData(httpGet,new TypeReference<List<DeliveryRecord>>(){},(a,b)-> a.stream().anyMatch(d-> {
             return Duration.between(d.getDeliveredAt(), ZonedDateTime.now()).toHours() > 24;
//             return d.getDeliveredAt().toLocalDate().isBefore(LocalDate.now());
         }));
        return dd.stream().flatMap(Collection::stream).toList();
    }

    public boolean reDeliveryFailures(String deliveryId) {
        HttpPost httpPost = new HttpPost("https://api.github.com/orgs/%s/hooks/%s/deliveries/%s/attempts".formatted(this.organization, this.webhookId, deliveryId));
        try(var client = createHttpClient(); var response = client.execute(httpPost)){
            if(response.getCode() >= 200 && 300 > response.getCode()){
                return true;
            }
        }catch (Exception e ){
            String msg = "Failed to redeliver id: %s".formatted(deliveryId);
            log.error(msg, e);
        }
        return false;
    }

    private <T> List<T> fetchPaginatedData(HttpUriRequestBase request, TypeReference<T> clazz)
            throws GeneralSecurityException, IOException, ProtocolException {
       return fetchPaginatedData(request, clazz, (a,b)->false);
    }
    private <T> List<T> fetchPaginatedData(HttpUriRequestBase request, TypeReference<T> clazz, BiPredicate<T, Integer> shouldStop)
            throws GeneralSecurityException, IOException, ProtocolException {
        List<T> results = new ArrayList<>();
        Function<String, T> func = (json)-> {
            try {
                T t = OBJECT_MAPPER.readValue(json, clazz);
                results.add(t);
                return t;
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };

        try (var client = createHttpClient(); var response = client.execute(request)) {

            collectPaginatedResponses(response, client,func,shouldStop);
            if(response.getCode()>=300){
                log.error("Received response code %s from request %s".formatted(response.getCode(),request));
            }
            return results;
        }
    }

    private <T>void collectPaginatedResponses(CloseableHttpResponse response, CloseableHttpClient client,  Function<String, T> pageProcessor, BiPredicate<T, Integer> shouldStop)
            throws IOException, ProtocolException {
        Header linkHeader;
        int count = 1;
        do {
            T processed = pageProcessor.apply(new String(response.getEntity().getContent().readAllBytes()));
            if(shouldStop.test(processed,count)){
                return;
            }
             linkHeader = response.getHeader("link");
            if (linkHeader != null) {
                String nextUrl = Arrays.stream(linkHeader.getValue().split(","))
                        .filter(link -> link.contains("rel=\"next\""))
                        .map(link -> link.split(";")[0].replace("<", "").replace(">", "").trim())
                        .findFirst()
                        .orElse(null);
                response = client.execute(new HttpGet(nextUrl));
            }
            count++;
        }while(linkHeader != null );
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
