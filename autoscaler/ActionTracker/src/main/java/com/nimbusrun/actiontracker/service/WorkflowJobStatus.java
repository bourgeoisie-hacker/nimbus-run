package com.nimbusrun.actiontracker.service;

import lombok.Getter;

public enum WorkflowJobStatus {
    COMPLETED("completed"),IN_PROGRESS("in_progress"),QUEUED("queued"),WAITING("waiting"), UNKNOWN("unknown");
    @Getter
    private final String status;
    WorkflowJobStatus(String status){
        this.status = status;
    }

    public static WorkflowJobStatus fromString(String s){
        s = s.toLowerCase();
        try{
           return WorkflowJobStatus.valueOf(s);
        }catch (Exception e){
            return UNKNOWN;
        }
    }

    public static boolean isActiveStatus(WorkflowJobStatus status){
        return switch (status){
            case QUEUED -> false;//UNKNOWN is treated as Active
            default -> true;
        };
    }
}
