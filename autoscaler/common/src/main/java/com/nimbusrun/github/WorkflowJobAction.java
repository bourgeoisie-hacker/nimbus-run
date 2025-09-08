package com.nimbusrun.github;

//Can be one of: requested, in_progress, completed, queued, pending, waiting
public enum WorkflowJobAction {
  COMPLETED("completed", 0), WAITING("waiting", 1), IN_PROGRESS("in_progress", 2), QUEUED("queued", 3),  UNKNOWN(
      "unknown", 9001);
  private final String status;
  private final int order;
  WorkflowJobAction(String status, int order) {
    this.status = status;
    this.order = order;
  }

  public static WorkflowJobAction fromString(String s) {

    for (var w : WorkflowJobAction.values()) {
      if (w.getStatus().equalsIgnoreCase(s)) {
        return w;
      }
    }
    return UNKNOWN;
  }

  public static boolean isActiveStatus(WorkflowJobAction status) {
    return switch (status) {
      case QUEUED -> false;//UNKNOWN is treated as Active
      default -> true;
    };
  }

  public String getStatus() {
    return status;
  }

  public int getOrder() {
    return order;
  }
}
