package com.nimbusrun;

import lombok.Data;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;

@Data
public class GithubResources {
  private final GHOrganization org;
  private final GHRepository repository;

  public GithubResources(GHOrganization org, GHRepository repository) {
    this.org = org;
    this.repository = repository;
  }
}
