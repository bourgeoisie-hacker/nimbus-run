package com.nimbusrun.admin;

import com.nimbusrun.github.GithubActionJob;
import com.nimbusrun.github.GithubActionRun;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import lombok.Getter;

public class WorkflowManager {

  @Getter
  private final String id;
  @Getter
  private final String name;
  @Getter
  private final String repositoryName;

  private volatile boolean relatedToNimbusRun;
  private final CopyOnWriteArrayList<GithubActionJob> githubActionJobs ;
  private final CopyOnWriteArrayList<GithubActionRun> githubActionRuns;
  public WorkflowManager(String id, String name, String repositoryName){
    this.id = id;
    this.name = name;
    this.repositoryName = repositoryName;
    this.githubActionRuns = new CopyOnWriteArrayList<>();
    this.githubActionJobs = new CopyOnWriteArrayList<>();
  }

  public void add(GithubActionRun run){
    this.githubActionRuns.add(run);
  }
  public void addRelatedToNimbusRun(GithubActionJob job){
    this.githubActionJobs.add(job);
    this.relatedToNimbusRun = true;
  }
  public void add(GithubActionJob job){
    this.githubActionJobs.add(job);
  }

  public Stream<GithubActionJob> getJobs(){
    return  this.githubActionJobs.stream();
  }
  public Stream<GithubActionRun> getRuns(){
    return  this.githubActionRuns.stream();
  }
  public Optional<GithubActionRun> getLatestRun(){
   return this.githubActionRuns.stream().min(GithubActionRun::compareTo);
  }


}
