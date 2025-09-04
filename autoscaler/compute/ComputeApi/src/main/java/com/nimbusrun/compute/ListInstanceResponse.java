package com.nimbusrun.compute;

import java.util.ArrayList;
import java.util.List;


public record ListInstanceResponse(List<Instance> instances) {

  public ListInstanceResponse(List<Instance> instances) {
    this.instances = new ArrayList<>(instances);
  }

  public static class Instance {

    private final String name;
    private final String instanceId;
    private final String instanceName;
    private final Long instanceCreateTimeInMilli;
    /**
     * This could be the actual Instance object itself.
     */
    private Object extraProperties;

    public Instance(String name, String instanceId, String instanceName,
        Long instanceCreateTimeInMilli) {
      this.name = name;
      this.instanceId = instanceId;
      this.instanceName = instanceName;
      this.instanceCreateTimeInMilli = instanceCreateTimeInMilli;
    }

    public Instance(String name, String instanceId, String instanceName,
        Long instanceCreateTimeInMilli, Object extraProperties) {
      this.name = name;
      this.instanceId = instanceId;
      this.instanceName = instanceName;
      this.instanceCreateTimeInMilli = instanceCreateTimeInMilli;
      this.extraProperties = extraProperties;
    }

    public String getName() {
      return name;
    }

    public String getInstanceId() {
      return instanceId;
    }

    public String getInstanceName() {
      return instanceName;
    }

    public Long getInstanceCreateTimeInMilli() {
      return instanceCreateTimeInMilli;
    }

    public Object getExtraProperties() {
      return extraProperties;
    }

    public void setExtraProperties(Object extraProperties) {
      this.extraProperties = extraProperties;
    }
  }
}
