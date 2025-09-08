package com.nimbusrun.admin.controller;

import com.nimbusrun.github.GithubActionRun;

public record WorkflowRunResponse(String id, String name, String status, Long createdAt,
                                  Long completedAt ) {
  public static WorkflowRunResponse fromRun(GithubActionRun run){
    return new WorkflowRunResponse(run.getId(), run.getName(), run.getAction().getStatus(), run.getCreatedAt(), run.getCreatedAt());
  }

}
