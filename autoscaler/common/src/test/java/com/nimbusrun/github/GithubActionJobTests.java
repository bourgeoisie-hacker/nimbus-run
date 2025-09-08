package com.nimbusrun.github;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)

public class GithubActionJobTests {

  @Value("classpath:workflow_jobs/completed_workflow_job.json")
  Resource completedWorkflowJob;

  @Value("classpath:workflow_jobs/in_progress_workflow_job-invalid-labels.json")
  Resource inProgressInvalidLabels;
//  testInProgressGithubJobInvalidLabels

  @Test
  public void testCompletedGithubJob() throws IOException {
    String content = completedWorkflowJob.getContentAsString(Charset.defaultCharset());
    GithubActionJob job = GithubActionJob.fromJson(new JSONObject(content));
    Assertions.assertEquals("49761151657", job.getId());
    Assertions.assertEquals("17519374189", job.getRunId());
    Assertions.assertEquals(WorkflowJobAction.COMPLETED, job.getAction());
    Assertions.assertEquals("test", job.getWorkflowName());
    Assertions.assertEquals("https://htmllinksomeweher.com", job.getHtmlUrl());
    Assertions.assertEquals("test", job.getName());
    Assertions.assertEquals("success", job.getConclusion());
    Assertions.assertEquals(1757191719000L, job.getStartedAt());
    Assertions.assertEquals(1757191781000L, job.getCompletedAt());
    Assertions.assertEquals("https://api.someurl.github.com", job.getRunUrl());
    Assertions.assertLinesMatch(List.of("action-group=prod", "action-pool=one"), job.getLabels());
    Assertions.assertLinesMatch(List.of(), job.getInvalidLabels());
    Assertions.assertTrue(job.getActionGroupName().isPresent() );
    Assertions.assertEquals("prod", job.getActionGroupName().get());
    Assertions.assertTrue(job.getActionPoolName().isPresent());
    Assertions.assertEquals("one", job.getActionPoolName().get());
    Assertions.assertEquals("bourgeoisie-whacker/test", job.getRepositoryFullName());
  }
  @Test
  public void testInProgressGithubJobInvalidLabels() throws IOException {
    String content = inProgressInvalidLabels.getContentAsString(Charset.defaultCharset());
    GithubActionJob job = GithubActionJob.fromJson(new JSONObject(content));
    Assertions.assertEquals("49761151657", job.getId());
    Assertions.assertEquals("17519374189", job.getRunId());
    Assertions.assertEquals(WorkflowJobAction.IN_PROGRESS, job.getAction());
    Assertions.assertEquals("test", job.getWorkflowName());
    Assertions.assertEquals("https://htmllinksomeweher.com", job.getHtmlUrl());
    Assertions.assertEquals("test", job.getName());
    Assertions.assertEquals("null", job.getConclusion());
    Assertions.assertEquals(1757191719000L, job.getStartedAt());
    Assertions.assertEquals(null, job.getCompletedAt());
    Assertions.assertEquals("https://api.someurl.github.com", job.getRunUrl());
    Assertions.assertLinesMatch(List.of("action-group=prod", "action-pool=one", "gpu-arm64"), job.getLabels());
    Assertions.assertLinesMatch(List.of("gpu-arm64"), job.getInvalidLabels());
    Assertions.assertTrue(job.getActionGroupName().isPresent() );
    Assertions.assertEquals("prod", job.getActionGroupName().get());
    Assertions.assertTrue(job.getActionPoolName().isPresent());
    Assertions.assertEquals("one", job.getActionPoolName().get());
    Assertions.assertEquals("bourgeoisie-whacker/test", job.getRepositoryFullName());
  }


}
