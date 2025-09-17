package com.nimbusrun.orm.aws;// pom: lombok + jackson
// <dependency>org.projectlombok:lombok</dependency>

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

import java.util.Map;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class AwsActionPoolsConfig {
    private String computeType;
    private String runnerGroup;
    private Map<String, ActionPool> actionPool;

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ActionPool {
        private String name;
        private String region;
        private String instanceType;
        private int maxInstanceCount;
        private int idleScaleDownInMinutes;
        private String credentialsProfile;
        private String subnet;
        private List<String> securityGroups;
        private DiskSettings diskSettings;
        private String architecture;
        private String os;
        private String keyPairName;
        @JsonProperty("default") private boolean defaultPool;
        private String credentialsProfileOpt;
        private String keyPairNameOpt;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DiskSettings {
        private String type;
        private int size;
        private Integer iops;
    }
}
