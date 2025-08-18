package com.nimbusrun.autoscaler.config;

import com.nimbusrun.logging.LogLevel;
import lombok.Data;
import lombok.Getter;
@Data
public class BaseConfig {

    @Data
    public static class KafkaConfig{
        private String retryTopic;
        private String webhookTopic;
        private String broker;
        private String consumerGroupId;
    }

    @Data
    public static class GithubConfig{
        private String groupName;
        private String organizationName;
        private String token;
        private String webhookSecret;
    }

    private String name;
    private KafkaConfig kafka;
    private GithubConfig github;
    private Object compute;
    private String computeType;
    private LogLevel logLevel;
    private String standalone;
}
