package com.nimbusrun.actiontracker.storage;

public interface Storage {
    public void get();
}
/*
    Cache Full
    key: WorkflowJob_id
    value: full object?

    Cache Queued:
    key: WorkflowJob_id

    Cache progress:
    key: WorkflowJob_id

    Cache finished:
    key: WorkflowJob_id

    Have a process that deletes workflow_ids after hours or days?
 */
