package com.nimbusrun.actiontracker.actiontracker;

import com.nimbusrun.github.GithubActionJob;
import lombok.Data;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

@Data
public class WorkflowJobWatcher {
    private String runId;
    private String jobId;

    Set<GithubActionJob> githubActionJobs = new ConcurrentSkipListSet<>();

    public static WorkflowJobWatcher fromGithubActionJob(GithubActionJob gj){
        return new WorkflowJobWatcherBuilder()
                .withRunId(gj.getRunId())
                .withJobId(gj.getId()).build();
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;
        WorkflowJobWatcher that = (WorkflowJobWatcher) object;
        return Objects.equals(runId, that.runId) && Objects.equals(jobId, that.jobId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(runId, jobId);
    }

    public static final class WorkflowJobWatcherBuilder {
        private String jobId;
        private String runId;

        private WorkflowJobWatcherBuilder() {
        }

        public static WorkflowJobWatcherBuilder aWorkflowJobWatcher() {
            return new WorkflowJobWatcherBuilder();
        }

        public WorkflowJobWatcherBuilder withJobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public WorkflowJobWatcherBuilder withRunId(String runId) {
            this.runId = runId;
            return this;
        }

        public WorkflowJobWatcher build() {
            WorkflowJobWatcher workflowJobWatcher = new WorkflowJobWatcher();
            workflowJobWatcher.setJobId(jobId);
            workflowJobWatcher.setRunId(runId);
            return workflowJobWatcher;
        }
    }
}
