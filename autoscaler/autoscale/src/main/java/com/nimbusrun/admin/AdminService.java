package com.nimbusrun.admin;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusrun.autoscaler.autoscaler.ValidWorkFlowJob;
import com.nimbusrun.compute.ActionPool;
import com.nimbusrun.config.ConfigReader;
import com.nimbusrun.github.GithubActionJob;
import com.nimbusrun.github.GithubActionRun;
import com.nimbusrun.webhook.WebhookReceiver;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminService implements WebhookReceiver {

  private final Cache<String, WorkflowManager> githubActionJobCache;
  private final Map<String, ActionPool> actionPoolMap;
  private final String actionGroupName;

  public AdminService( ConfigReader configReader,@Value("${github.groupName}") String actionGroupName){
    this.githubActionJobCache = Caffeine.newBuilder()
        .maximumSize(1_000_000)
        .expireAfterWrite(Duration.ofDays(3))
        .build();
    this.actionPoolMap = configReader.getActionPoolMap();
    this.actionGroupName = actionGroupName;
  }
  @Override
  public boolean receive(GithubActionJob githubActionJob) {
    ValidWorkFlowJob vf = new ValidWorkFlowJob(githubActionJob,this.actionPoolMap, actionGroupName);
    WorkflowManager wm = getWorkflowManager(new Key(githubActionJob.getRunId(),githubActionJob.getWorkflowName(), githubActionJob.getRepositoryFullName()));
    if(!vf.isInvalid()){
      wm.addRelatedToNimbusRun(githubActionJob);
    }else{
      wm.add(githubActionJob);
    }
    return true;
  }

  @Override
  public boolean receive(GithubActionRun gr) {
    WorkflowManager wm = getWorkflowManager(new Key(gr.getId(),gr.getName(), gr.getRepositoryFullName()));
    wm.add(gr);
    return true;
  }



  private  WorkflowManager getWorkflowManager(Key id){
    WorkflowManager manager = this.githubActionJobCache.getIfPresent(id.id());
    if(manager == null){
      synchronized (this.githubActionJobCache){
        WorkflowManager check = this.githubActionJobCache.getIfPresent(id.id());
          if(check  == null){
            this.githubActionJobCache.get(id.id(), (i)-> new WorkflowManager(id.id(), id.name(),id.repositoryName()));
          }
      }
      manager = this.githubActionJobCache.getIfPresent(id.id());
    }
    return manager;
  }
  public Collection<WorkflowManager> getWorkflowManagers(){
    return this.githubActionJobCache.asMap().values();
  }

  public  record Key(String id, String name, String repositoryName){ }

}
