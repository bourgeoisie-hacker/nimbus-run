package com.nimbusrun.actiontracker.config;

import com.nimbusrun.logging.LogLevel;
import lombok.Data;

@Data
public class ActionTrackerConfig {

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
    }
    private String name;
    private KafkaConfig kafka;
    private GithubConfig github;
    private LogLevel logLevel;
}
