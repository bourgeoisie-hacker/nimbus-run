package com.nimbusrun.autoscaler.autoscaler;

import com.nimbusrun.compute.ActionPool;
import java.util.List;
import lombok.Data;
import lombok.Getter;

@Data
public class MockConfig {


  private List<MockActionPool> actionPools;

  public List<ActionPool> getActionPool(){
    return actionPools.stream().map(MockActionPool::toActionPool).toList();
  }
}
