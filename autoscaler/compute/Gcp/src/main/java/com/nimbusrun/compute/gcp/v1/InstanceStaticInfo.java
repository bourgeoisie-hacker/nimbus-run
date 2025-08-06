package com.nimbusrun.compute.gcp.v1;

import java.util.Objects;

public class InstanceStaticInfo {
    private final String instanceId;
    private final String zone;
    private final String name;
    public InstanceStaticInfo(String instanceId, String zone, String name) {
        this.instanceId = instanceId;
        this.zone = zone;
        this.name = name;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getZone() {
        return zone;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;
        InstanceStaticInfo that = (InstanceStaticInfo) object;
        return Objects.equals(instanceId, that.instanceId) && Objects.equals(zone, that.zone) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId, zone, name);
    }
}
