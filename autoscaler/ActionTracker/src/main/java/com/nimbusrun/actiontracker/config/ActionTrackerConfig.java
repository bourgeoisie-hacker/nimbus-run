package com.nimbusrun.actiontracker.config;

import lombok.Data;
import lombok.Getter;

@Data
public class ActionTrackerConfig {
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

    private String name;
    private KafkaConfig kafkaConfig;
    private GithubConfig githubConfig;
    private LogLevel logLevel;
}
