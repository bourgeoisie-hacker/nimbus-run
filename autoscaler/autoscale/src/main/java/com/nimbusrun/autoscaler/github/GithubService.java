
package com.nimbusrun.autoscaler.github;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }


    public GithubService(@Value("${github.token}") String token,
                         @Value("${github.organizationName}") String organization,
                         @Value("${github.groupName}") String runnerGroupName,
                         @Value("${spring.application.name}") String actionGroupName) {
        this.token = token;
        this.organization = organization;
        this.runnerGroupName = runnerGroupName;
        this.runnerGroupId = resolveRunnerGroupId(runnerGroupName);
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
                    ListRunnerGroup.class);
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
            var pages = fetchPaginatedData(request, ListSelfHostedRunners.class);
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
    private <T> List<T> fetchPaginatedData(HttpUriRequestBase request, Class<T> clazz)
            throws GeneralSecurityException, IOException, ProtocolException {
        try (var client = createHttpClient(); var response = client.execute(request)) {
            List<String> pages = new ArrayList<>();
            collectPaginatedResponses(response, client, pages);
            if(response.getCode()>=300){
                log.error("Received response code %s from request %s".formatted(response.getCode(),request));
            }
            List<T> results = new ArrayList<>();
            for (String json : pages) {
                results.add(OBJECT_MAPPER.readValue(json, clazz));
            }
            return results;
        }
    }

    private void collectPaginatedResponses(CloseableHttpResponse response, CloseableHttpClient client, List<String> pages)
            throws IOException, ProtocolException {
        pages.add(new String(response.getEntity().getContent().readAllBytes()));
        var linkHeader = response.getHeader("link");
        if (linkHeader != null) {
            Arrays.stream(linkHeader.getValue().split(","))
                    .filter(link -> link.contains("rel=\"next\""))
                    .map(link -> link.split(";")[0].replace("<", "").replace(">", "").trim())
                    .findFirst()
                    .ifPresent(nextUrl -> {
                        try (var nextResp = client.execute(new HttpGet(nextUrl))) {
                            collectPaginatedResponses(nextResp, client, pages);
                        } catch (IOException | ProtocolException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
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
