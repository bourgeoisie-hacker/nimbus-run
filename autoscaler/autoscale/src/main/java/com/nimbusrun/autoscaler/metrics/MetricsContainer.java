package com.nimbusrun.autoscaler.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;

@Component
public class MetricsContainer {

    public static final String CREATE_OPERATION = "create";
    public static final String DELETE_OPERATION = "delete";
    public static final String POOL_NAME_TAG = "pool_name";
    public static String INSTANCE_OPERATIONS_TOTAL = "instance_operations_total";
    public static String INSTANCE_COUNT = "instance_count";
    public static String INSTANCE_CREATE_RETRIES = "instance_create_retries_total";
    /*TODO
        - counter user trigger a workflow_run
        - counter repository triggering workflow_run
     */
    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicInteger> actionPoolGaugeMap = new ConcurrentHashMap<>();
    public MetricsContainer(MeterRegistry meterRegistry){
        this.meterRegistry = meterRegistry;
    }

    public void updateInstanceCount(String actionPoolName, int instanceCount){

        AtomicInteger mapAi = actionPoolGaugeMap.computeIfAbsent(actionPoolName, (key)-> {
            AtomicInteger ai = new AtomicInteger(instanceCount);
            Gauge.builder(INSTANCE_COUNT, ai::get).tag(POOL_NAME_TAG, actionPoolName).register(this.meterRegistry);
            return ai;
        });
        mapAi.set(instanceCount);
    }

    public void instanceRetriesPoolFull(String actionPoolName){
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of(POOL_NAME_TAG, actionPoolName));
        tags.add(Tag.of("type", "full"));
        Counter.builder(INSTANCE_CREATE_RETRIES).tags(tags).register(meterRegistry).increment();
    }
    public void instanceRetriesFailedCreate(String actionPoolName){
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of(POOL_NAME_TAG, actionPoolName));
        tags.add(Tag.of("type", "failed"));
        Counter.builder(INSTANCE_CREATE_RETRIES).tags(tags).register(meterRegistry).increment();
    }
    public void instanceCreatedTotal(String actionPoolName, boolean success){
        instanceOperations(actionPoolName, success, CREATE_OPERATION);
    }

    public void instanceDeletedTotal(String actionPoolName, boolean success){
        instanceOperations(actionPoolName, success, DELETE_OPERATION);
    }

    private void instanceOperations(String actionPoolName, boolean success, String operation){
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of(POOL_NAME_TAG, actionPoolName));
        tags.add(Tag.of("type", operation));
        tags.add(Tag.of("result", success? "success": "failure"));
        Counter.builder(INSTANCE_OPERATIONS_TOTAL).tags(tags).register(meterRegistry).increment();

    }


}
