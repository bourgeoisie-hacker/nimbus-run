package com.nimbusrun.webhook.config;

import com.nimbusrun.logging.LogLevel;
import lombok.Data;
import lombok.Getter;

@Data
public class WebHookConfig {
    @Data
    public static class KafkaConfig{
        private String webhookTopic;
        private String broker;
    }
    @Data
    public static class GithubConfig{
        private String webhookSecret;
    }
    private String name;
    private KafkaConfig kafka;
    private GithubConfig github;
    private LogLevel logLevel;
}
