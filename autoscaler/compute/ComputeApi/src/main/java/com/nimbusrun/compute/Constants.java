package com.nimbusrun.compute;

import java.util.Map;

public class Constants {
    public static final int DEFAULT_TIME_BETWEEN_SCALE_UPS_IN_SECONDS = 2;
    public static final int DEFAULT_MAX_INSTANCES = 10;
    public static final int DELETE_INSTANCE_RUNNER_THRESHOLD = 3;
    public static final int DEFAULT_INSTANCE_IDLE_TIME_IN_MINUTES = 10;
    public static final String ACTION_POOL_LABEL_KEY = "action-pool";
    public static final String ACTION_GROUP_LABEL_KEY = "action-group";
    public static boolean mapContainsKeyAndNotNullValue(Map map, String key){
        if(map.containsKey(key) && map.get(key) != null){
            return true;
        }
        return false;
    }
}
