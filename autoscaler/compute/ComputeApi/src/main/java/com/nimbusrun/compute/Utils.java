package com.nimbusrun.compute;

import org.slf4j.Logger;

public class Utils {
    public static void excessiveErrorLog(String msg, Throwable throwable, Logger log){
        if(log.isDebugEnabled() && throwable != null){
            log.error(msg,throwable);
        }
        else{
            log.error(msg);
        }
    }
}
