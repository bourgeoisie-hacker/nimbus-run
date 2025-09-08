package com.nimbusrun.admin.controller;

import com.nimbusrun.github.GithubActionJob;

public record WorkflowJobResponse(String id, String name, String status, Long startedAt,
                                  Long completedAt, String actionPoolName) {

  public static WorkflowJobResponse fromJob(GithubActionJob job){
    return new WorkflowJobResponse(job.getId(), job.getName(), job.getAction().getStatus(), job.getStartedAt(),
        job.getCompletedAt(), job.getActionPoolName().orElse("null"));
  }
}
