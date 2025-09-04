package com.nimbusrun;

import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;

public class Utils {

  public static void excessiveErrorLog(String msg, Throwable throwable, Logger log) {
    if (log.isDebugEnabled() && throwable != null) {
      log.error(msg, throwable);
    } else {
      log.error(msg);
    }
  }

  public static boolean mapContainsKeyAndNotNullValue(Map map, String key) {
    if (map.containsKey(key) && map.get(key) != null) {
      return true;
    }
    return false;
  }

  public static void setStringValueFromMap(Map<String, Object> map, String key,
      Consumer<String> consumer) {
    if (Utils.mapContainsKeyAndNotNullValue(map, key)) {
      consumer.accept(map.get(key).toString());
    }
  }
}
