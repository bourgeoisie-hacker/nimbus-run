package com.nimbusrun.autoscaler.github.orm.runnergroup;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ListRunnerGroup {

  private Integer totalCount;
  private List<RunnerGroup> runnerGroups = new ArrayList<RunnerGroup>();

}