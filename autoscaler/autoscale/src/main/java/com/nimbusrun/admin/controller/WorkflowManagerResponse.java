package com.nimbusrun.admin.controller;

import com.nimbusrun.admin.WorkflowManager;
import com.nimbusrun.github.GithubActionJob;
import com.nimbusrun.github.GithubActionRun;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record WorkflowManagerResponse(String id, String name, String repositoryName, String runHtml, String status, Long createdAt, Long latestUpdated, List<WorkflowJobResponse> jobs, List<WorkflowRunResponse> runs) {

  public static WorkflowManagerResponse fromWorkflowManager(WorkflowManager workflowManager){
    List<WorkflowJobResponse> jobs = workflowManager.getJobs().sorted(
        Comparator.comparing((GithubActionJob i)->i).reversed()).map(WorkflowJobResponse::fromJob).distinct().toList();
    List<WorkflowRunResponse> runs = workflowManager.getRuns().sorted(
        Comparator.comparing((GithubActionRun i)->i).reversed()).map(WorkflowRunResponse::fromRun).distinct().toList();
    String id = workflowManager.getId();
    String name = workflowManager.getName();
    String repo = workflowManager.getRepositoryName();
    String runHtml = "";
    String status = "";
    Long createdAt = 0L;
    Long updatedAt = 0L;
    Optional<GithubActionRun> latestRun =workflowManager.getLatestRun();
    if(latestRun.isPresent()){
      runHtml = latestRun.get().getRunHtmlUrl();
      status = latestRun.get().getAction().getStatus();
      createdAt = latestRun.get().getCreatedAt();
      updatedAt = latestRun.get().getUpdatedAt();
    }
    return new WorkflowManagerResponse(id,name, repo, runHtml, status, createdAt, updatedAt, jobs, runs);
  }
}
