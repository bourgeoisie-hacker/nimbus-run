package com.nimbusrun.autoscaler.github.orm.runnergroup;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RunnerGroup {

  private Integer id;
  private String name;
  private String visibility;
  private Boolean _default;
  private String runnersUrl;
  private Boolean inherited;
  private Boolean allowsPublicRepositories;
  private Boolean restrictedToWorkflows;
  private List<String> selectedWorkflows = new ArrayList<String>();
  private Boolean workflowRestrictionsReadOnly;
  private String selectedRepositoriesUrl;

}