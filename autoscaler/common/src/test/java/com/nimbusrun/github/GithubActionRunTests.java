package com.nimbusrun.github;

import java.io.IOException;
import java.nio.charset.Charset;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
public class GithubActionRunTests {

  @Value("classpath:workflow_runs/completed_workflow_run.json")
  Resource completedWorkflowRun;

  @Test
  public void testCompletedWorkflowRun() throws IOException {
    String content = completedWorkflowRun.getContentAsString(Charset.defaultCharset());
    GithubActionRun run = GithubActionRun.fromJson(new JSONObject(content));

    Assertions.assertEquals("test-os", run.getName());
    Assertions.assertEquals("17518051290", run.getId());
    Assertions.assertEquals("2", run.getRunNumber());
    Assertions.assertEquals("workflow_dispatch", run.getEvent());
    Assertions.assertEquals(WorkflowRunAction.COMPLETED, run.getAction());
    Assertions.assertEquals("http://somehtml.com", run.getHtmlUrl());
    Assertions.assertEquals("bourgeoisie-whacker/test", run.getRepositoryFullName());
    Assertions.assertEquals(1757183521000L, run.getUpdatedAt());
    Assertions.assertEquals(1757183327000L, run.getCreatedAt());
  }
}
