package com.nimbusrun.github;

//Can be one of: requested, in_progress, completed, queued, pending, waiting

public enum WorkflowRunAction {
  COMPLETED("completed", 0), IN_PROGRESS("in_progress", 2), REQUESTED("requested",3),  UNKNOWN("unknown",9001);
  private final String status;
  private final int order;
  WorkflowRunAction(String status, int order) {
    this.status = status;
    this.order = order;
  }

  public static WorkflowRunAction fromString(String s) {

    for (var w : WorkflowRunAction.values()) {
      if (w.getStatus().equalsIgnoreCase(s)) {
        return w;
      }
    }
    return UNKNOWN;
  }

  public String getStatus() {
    return status;
  }

  public int getOrder() {
    return order;
  }
}
