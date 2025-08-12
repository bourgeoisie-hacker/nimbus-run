package com.nimbusrun.github;


import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class GithubActionJob implements Comparable<GithubActionJob>{
    private final String id;
    private final String runId;
    private final WorkflowJobStatus status;

    private final String htmlUrl;
    private final String name;
    private final String conclusion;
    private final Long startedAt;
    private final Long completedAt;
    private final String runUrl;
    private final List<String> labels;
    private final String jsonStr;

    public GithubActionJob(String id, String runId, WorkflowJobStatus status, String htmlUrl, String name, String conclusion, Long startedAt, Long completedAt, String runUrl, List<String> labels, String jsonStr) {
        this.id = id;
        this.runId = runId;
        this.status = status;
        this.htmlUrl = htmlUrl;
        this.name = name;
        this.conclusion = conclusion;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.runUrl = runUrl;
        this.labels = labels;
        this.jsonStr = jsonStr;
    }

    public static GithubActionJob fromJson(JSONObject object){
        JSONObject job = object.getJSONObject("workflow_job");

        UnaryOperator<String> getStr = (key)->{
            if(job.has(key)){
                Object obj = job.get(key);
                if(obj == null){
                    return null;
                }
                return obj.toString();
            }
            return null;
        };
        String id = getStr.apply("id");
        String runId = getStr.apply("run_id");
        WorkflowJobStatus status = WorkflowJobStatus.fromString(getStr.apply("status"));
        String htmlUrl = getStr.apply("html_url");
        String name = getStr.apply("name");
        String conclusion = getStr.apply("conclusion");
        Long startedAt = Optional.ofNullable(getStr.apply("started_at")).map(i->{
            try{
                return Instant.parse(i).toEpochMilli();
            }catch (Exception e){}
            return null;
        }).orElse(null);
        Long completedAt = Optional.ofNullable(getStr.apply("completed_at")).map(i->{
            try{
                return Instant.parse(i).toEpochMilli();
            }catch (Exception e){}
            return null;
        }).orElse(null);

        String runUrl = job.getString("run_url");
        JSONArray labels = job.getJSONArray("labels");
        List<String> labelList = new ArrayList<>();
        for(int i = 0; i < labels.length(); i++){
            labelList.add(labels.getString(i));
        }

        return new GithubActionJobBuilder().withId(id).withRunId(runId).withStatus(status)
                .withHtmlUrl(htmlUrl).withName(name).withConclusion(conclusion).withStartedAt(startedAt)
                .withCompletedAt(completedAt)
                .withRunUrl(runUrl)
                .withLabels(labelList)
                .withJsonStr(object.toString()).build();
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;
        GithubActionJob that = (GithubActionJob) object;
        return Objects.equals(id, that.id) && Objects.equals(runId, that.runId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, runId);
    }

    @Override
    public int compareTo(GithubActionJob o) {
        return this.id.compareTo(o.getId());
    }

    public String getId() {
        return id;
    }

    public String getRunId() {
        return runId;
    }

    public WorkflowJobStatus getStatus() {
        return status;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public String getName() {
        return name;
    }

    public String getConclusion() {
        return conclusion;
    }

    public Long getStartedAt() {
        return startedAt;
    }

    public Long getCompletedAt() {
        return completedAt;
    }

    public String getJsonStr() {
        return jsonStr;
    }

    public String getRunUrl() {
        return runUrl;
    }

    public List<String> getLabels() {
        return labels;
    }

    public static final class GithubActionJobBuilder {
        private String id;
        private String runId;
        private WorkflowJobStatus status;
        private String htmlUrl;
        private String name;
        private String conclusion;
        private Long startedAt;
        private Long completedAt;
        private String runUrl;
        private List<String> labels;
        private String jsonStr;

        private GithubActionJobBuilder() {
        }

        public static GithubActionJobBuilder aGithubActionJob() {
            return new GithubActionJobBuilder();
        }

        public GithubActionJobBuilder withId(String id) {
            this.id = id;
            return this;
        }

        public GithubActionJobBuilder withRunId(String runId) {
            this.runId = runId;
            return this;
        }

        public GithubActionJobBuilder withStatus(WorkflowJobStatus status) {
            this.status = status;
            return this;
        }

        public GithubActionJobBuilder withHtmlUrl(String htmlUrl) {
            this.htmlUrl = htmlUrl;
            return this;
        }

        public GithubActionJobBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public GithubActionJobBuilder withConclusion(String conclusion) {
            this.conclusion = conclusion;
            return this;
        }

        public GithubActionJobBuilder withStartedAt(Long startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public GithubActionJobBuilder withCompletedAt(Long completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public GithubActionJobBuilder withRunUrl(String runUrl) {
            this.runUrl = runUrl;
            return this;
        }

        public GithubActionJobBuilder withLabels(List<String> labels) {
            this.labels = labels;
            return this;
        }

        public GithubActionJobBuilder withJsonStr(String jsonStr) {
            this.jsonStr = jsonStr;
            return this;
        }

        public GithubActionJob build() {
            return new GithubActionJob(id, runId, status, htmlUrl, name, conclusion, startedAt, completedAt, runUrl, labels, jsonStr);
        }
    }
}
