package com.nimbusrun.actiontracker.service;

import lombok.Data;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Data
public class GithubActionJob implements Comparable<GithubActionJob>{
    private final String id;
    private final String runId;
    private final WorkflowJobStatus status;

    private final String htmlUrl;
    private final String name;
    private final String conclusion;
    private final Long startedAt;
    private final Long completedAt;
    private final String jsonStr;

    public static GithubActionJob fromJson(JSONObject object){
        JSONObject job = object.getJSONObject("workflow_job");
        String id = job.getString("id");
        String runId = job.getString("run_id");
        WorkflowJobStatus status = WorkflowJobStatus.fromString(job.getString("status"));
        String htmlUrl = job.getString("html_url");
        String name = job.getString("name");
        String conclusion = job.getString("conclusion");
        Long startedAt = Optional.ofNullable(job.getString("started_at")).map(i->{
            try{
                return Instant.parse(i).toEpochMilli();
            }catch (Exception e){}
            return null;
        }).orElse(null);
        Long completedAt = Optional.ofNullable(job.getString("completed_at")).map(i->{
            try{
                return Instant.parse(i).toEpochMilli();
            }catch (Exception e){}
            return null;
        }).orElse(null);
        return new GithubActionJobBuilder().withId(id).withRunId(runId).withStatus(status)
                .withHtmlUrl(htmlUrl).withName(name).withConclusion(conclusion).withStartedAt(startedAt)
                .withCompletedAt(completedAt).withJsonStr(object.toString()).build();
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

    public static final class GithubActionJobBuilder {
        private String id;
        private String runId;
        private WorkflowJobStatus status;
        private String htmlUrl;
        private String name;
        private String conclusion;
        private long startedAt;
        private long completedAt;
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

        public GithubActionJobBuilder withStartedAt(long startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public GithubActionJobBuilder withCompletedAt(long completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public GithubActionJobBuilder withJsonStr(String jsonStr) {
            this.jsonStr = jsonStr;
            return this;
        }

        public GithubActionJob build() {
            return new GithubActionJob(id, runId, status, htmlUrl, name, conclusion, startedAt, completedAt, jsonStr);
        }
    }
}
