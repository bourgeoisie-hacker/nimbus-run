package com.nimbusrun.compute;


import java.util.Objects;
import java.util.Optional;

public class ActionPool {

  private final String name;
  private final Integer maxInstances;
  private final Integer instanceIdleScaleDownTimeInMinutes;
  private final boolean isDefault;

  public ActionPool(String name, Integer maxInstances, Integer instanceIdleScaleDownTimeInMinutes,
      boolean isDefault) {
    this.name = name;
    this.maxInstances = maxInstances;
    this.instanceIdleScaleDownTimeInMinutes = instanceIdleScaleDownTimeInMinutes;
    this.isDefault = isDefault;
  }

  public Optional<Integer> getMaxInstances() {
    return Optional.ofNullable(this.maxInstances);
  }

  public Optional<Integer> getInstanceIdleScaleDownTimeInMinutes() {
    return Optional.ofNullable(instanceIdleScaleDownTimeInMinutes);
  }

  public String getName() {
    return name;
  }

  public boolean isDefault() {
    return isDefault;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ActionPool that = (ActionPool) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }
}
