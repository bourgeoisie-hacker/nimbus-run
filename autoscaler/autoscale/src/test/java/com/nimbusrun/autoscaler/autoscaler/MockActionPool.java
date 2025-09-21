package com.nimbusrun.autoscaler.autoscaler;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nimbusrun.compute.ActionPool;
import java.util.Objects;
import java.util.Optional;
import lombok.Data;

@Data
public class MockActionPool {

  private  String name;
  private  Integer maxInstances;
  private  Integer instanceIdleScaleDownTimeInMinutes;

  @JsonProperty("default")
  private  boolean isDefault;
  public ActionPool toActionPool(){
    return  new ActionPool(name, maxInstances, instanceIdleScaleDownTimeInMinutes, isDefault);
  }

}
