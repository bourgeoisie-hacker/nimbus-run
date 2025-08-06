package com.nimbusrun.autoscaler.autoscaler;

import com.nimbusrun.compute.ActionPool;
import lombok.Data;

import java.util.Optional;

@Data
public class InstanceScalerTimeTracker {
    private final ActionPool actionPool;
    private long lastScaleUpTime;

    public InstanceScalerTimeTracker(ActionPool actionPool) {
        this.actionPool = actionPool;
    }

    public Optional<Long> getWaitTimeBeforeScaleUp(long timeBeforeScaleAction) {
        if (System.currentTimeMillis() > lastScaleUpTime + timeBeforeScaleAction) {
            return Optional.empty();
        }else {
            return Optional.of(lastScaleUpTime + timeBeforeScaleAction - System.currentTimeMillis());
        }
    }

}
