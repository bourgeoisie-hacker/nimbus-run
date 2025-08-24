package com.nimbusrun.config;

import com.nimbusrun.logging.LogLevel;
import lombok.Data;

@Data
public class BaseConfig {

    @Data
    public static class RetryPolicy {
        private Integer maxJobInQueuedInMinutes;
        private Integer maxTimeBtwRetriesInMinutes;
        private Integer maxRetries;
    }


    @Data
    public static class GithubConfig {
        private String groupName;
        private String organizationName;
        private String token;
        private String webhookSecret;
    }

    private String name;
    private RetryPolicy retryPolicy;
    private GithubConfig github;
    private Object compute;
    private String computeType;
    private LogLevel logLevel;
    private String standalone;
}
