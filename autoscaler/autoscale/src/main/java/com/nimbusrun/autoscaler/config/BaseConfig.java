package com.nimbusrun.autoscaler.config;

import lombok.Data;
import lombok.Getter;
@Data
public class BaseConfig {
    public enum LogLevel{
        INFO("info"), WARN("warn"), ERROR("error"), DEBUG("debug"), VERBOSE("verbose"), UNKNOWN("unknown"), N_A("n/a");
        @Getter
        private String level;
        private LogLevel(String level){
            this.level = level;
        }
        public static LogLevel fromStr(String l){
            if(l == null){
                return N_A;
            }
            for(var level : LogLevel.values()){
                if(level.level.equalsIgnoreCase(l)){
                    return level;
                }
            }

            return UNKNOWN;
        }

    }
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
