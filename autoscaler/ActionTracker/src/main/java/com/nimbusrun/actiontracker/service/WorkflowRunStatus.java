package com.nimbusrun.actiontracker.service;

import lombok.Getter;

public enum WorkflowRunStatus {
    COMPLETED("completed"),IN_PROGRESS("in_progress"),REQUESTED("request");
    @Getter
    private final String status;
    WorkflowRunStatus(String status){
        this.status = status;
    }

}
