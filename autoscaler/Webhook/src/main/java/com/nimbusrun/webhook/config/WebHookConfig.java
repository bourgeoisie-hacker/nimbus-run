package com.nimbusrun.webhook.config;

import lombok.Data;
import lombok.Getter;

@Data
public class WebHookConfig {
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
