package com.nimbusrun.compute;


import java.util.Objects;

public class DeleteInstanceRequest {

  private final ActionPool actionPool;
  private final String instanceId;
  private final String instanceName;
  private final Object extraProperties;

  public DeleteInstanceRequest(ActionPool actionPool, String instanceId, String instanceName,
      Object extraProperties) {
    this.actionPool = actionPool;
    this.instanceId = instanceId;
    this.instanceName = instanceName;
    this.extraProperties = extraProperties;
  }

  public ActionPool getActionPool() {
    return actionPool;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public Object getExtraProperties() {
    return extraProperties;
  }

  @Override
  public boolean equals(Object object1) {
    if (object1 == null || getClass() != object1.getClass()) {
      return false;
    }
    DeleteInstanceRequest that = (DeleteInstanceRequest) object1;
    return Objects.equals(actionPool, that.actionPool) && Objects.equals(instanceId,
        that.instanceId) && Objects.equals(extraProperties, that.extraProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(actionPool, instanceId, extraProperties);
  }
}
