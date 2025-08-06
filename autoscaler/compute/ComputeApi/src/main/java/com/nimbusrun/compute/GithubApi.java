package com.nimbusrun.compute;


import java.util.Optional;


public interface GithubApi {

    public Optional<String> generateRunnerToken();
    public String getOrganization();

    public Integer getRunnerGroupId();

    public String getRunnerGroupName();
}
