package com.nimbusrun.actiontracker.actiontracker;

import lombok.Data;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
@Data
public class RetryTracker {
    private final String jobId;
    private final String runId;
    private final CopyOnWriteArrayList<Long> retryTimes = new CopyOnWriteArrayList<>();

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;
        RetryTracker that = (RetryTracker) object;
        return Objects.equals(jobId, that.jobId) && Objects.equals(runId, that.runId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, runId);
    }
}
