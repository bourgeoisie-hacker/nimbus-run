package com.nimbusrun.github;


public enum WorkflowJobStatus {
    COMPLETED("completed"),IN_PROGRESS("in_progress"),QUEUED("queued"),WAITING("waiting"), UNKNOWN("unknown");
    private final String status;
    WorkflowJobStatus(String status){
        this.status = status;
    }

    public static WorkflowJobStatus fromString(String s){

        for(var w : WorkflowJobStatus.values()){
            if(w.getStatus().equalsIgnoreCase(s)){
                return w;
            }
        }
        return UNKNOWN;
    }

    public static boolean isActiveStatus(WorkflowJobStatus status){
        return switch (status){
            case QUEUED -> false;//UNKNOWN is treated as Active
            default -> true;
        };
    }

    public String getStatus() {
        return status;
    }
}
