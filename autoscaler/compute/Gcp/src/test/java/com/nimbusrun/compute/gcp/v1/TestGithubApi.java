package com.nimbusrun.compute.gcp.v1;

import com.nimbusrun.compute.GithubApi;
import java.util.Optional;
import java.util.Random;

public class TestGithubApi implements GithubApi {

  private final String org;
  private final int runnerGroupId;
  private final String runnerGroupName;

  public TestGithubApi(String org, int runnerGroupId, String runnerGroupName){
    this.org = org;
    this.runnerGroupId = runnerGroupId;
    this.runnerGroupName = runnerGroupName;
  }

  @Override
  public Optional<String> generateRunnerToken() {
    return Optional.of(new Random().nextInt(100, 3999)+"");
  }

  @Override
  public String getOrganization() {
    return this.org;
  }

  @Override
  public Integer getRunnerGroupId() {
    return this.runnerGroupId;
  }

  @Override
  public String getRunnerGroupName() {
    return this.runnerGroupName;
  }

  @Override
  public boolean isJobQueued(String runUrl) {
    return true;
  }
}
