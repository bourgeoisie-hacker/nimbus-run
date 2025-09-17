package com.nimbusrun.orm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostedWebhook {
    private String id;
    private String name;
    private String repositoryName;
    private String runHtml;
    private String status;
    private Long createdAt;       // epoch millis
    private Long latestUpdated;   // epoch millis

    private List<Job> jobs = new ArrayList<>();

    private List<RunRecord> runs = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Job {
        private String id;
        private String name;
        private String status;
        private Long startedAt;     // epoch millis
        private Long completedAt;   // nullable epoch millis
        private String actionPoolName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RunRecord {
        private String id;
        private String name;
        private String status;
        private Long createdAt;     // epoch millis
        private Long completedAt;   // epoch millis (can be null if needed)
    }

    public static void main(String[] args) throws JsonProcessingException {
        String s = "[{\"id\":\"17519374189\",\"name\":\"test\",\"repositoryName\":\"bourgeoisie-whacker/test\",\"runHtml\":\"https://github.com/bourgeoisie-whacker/test/actions/runs/17519374189\",\"status\":\"completed\",\"createdAt\":1757191602000,\"latestUpdated\":1757191781000,\"jobs\":[{\"id\":\"49761151657\",\"name\":\"test\",\"status\":\"queued\",\"startedAt\":1757191603000,\"completedAt\":null,\"actionPoolName\":\"one\"},{\"id\":\"49761151657\",\"name\":\"test\",\"status\":\"in_progress\",\"startedAt\":1757191719000,\"completedAt\":null,\"actionPoolName\":\"one\"},{\"id\":\"49761151657\",\"name\":\"test\",\"status\":\"completed\",\"startedAt\":1757191719000,\"completedAt\":1757191781000,\"actionPoolName\":\"one\"}],\"runs\":[{\"id\":\"17519374189\",\"name\":\"test\",\"status\":\"requested\",\"createdAt\":1757191602000,\"completedAt\":1757191602000},{\"id\":\"17519374189\",\"name\":\"test\",\"status\":\"in_progress\",\"createdAt\":1757191602000,\"completedAt\":1757191602000},{\"id\":\"17519374189\",\"name\":\"test\",\"status\":\"completed\",\"createdAt\":1757191602000,\"completedAt\":1757191602000}]},{\"id\":\"17519374199\",\"name\":\"test\",\"repositoryName\":\"bourgeoisie-whacker/test\",\"runHtml\":\"\",\"status\":\"\",\"createdAt\":0,\"latestUpdated\":0,\"jobs\":[{\"id\":\"49761151657\",\"name\":\"test\",\"status\":\"in_progress\",\"startedAt\":1757191719000,\"completedAt\":null,\"actionPoolName\":\"one\"}],\"runs\":[]}]";
        ObjectMapper o = new ObjectMapper();

        System.out.println(o.readValue(s,  new com.fasterxml.jackson.core.type.TypeReference<List<PostedWebhook>>() {}));
    }
}
