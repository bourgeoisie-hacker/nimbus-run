package com.nimbusrun.autoscaler.autoscaler;

import com.nimbusrun.compute.ActionPool;
import com.nimbusrun.github.GithubActionJob;
import com.nimbusrun.github.WorkflowJobStatus;
import lombok.Getter;

import java.util.Map;
@Getter
public class ValidWorkFlowJob {

    private final boolean forGroup;
    private final boolean invalidLabels;
    private final boolean invalidActionPool;
    private final boolean workflowIsNotQueued;
    public ValidWorkFlowJob(GithubActionJob gj, Map<String, ActionPool> actionPoolMap, String runnerGroupName) {
        this.workflowIsNotQueued = gj.getStatus() != WorkflowJobStatus.QUEUED;
        this.forGroup = gj.getActionGroupName().isPresent() && gj.getActionGroupName().get().equalsIgnoreCase(runnerGroupName);
        this.invalidLabels = !gj.getInvalidLabels().isEmpty();
        this.invalidActionPool = gj.getActionPoolName().isPresent() && !actionPoolMap.containsKey(gj.getActionPoolName().get());
    }

    public boolean isInvalid(){
        return workflowIsNotQueued || !forGroup || invalidActionPool || invalidLabels;
    }
}
