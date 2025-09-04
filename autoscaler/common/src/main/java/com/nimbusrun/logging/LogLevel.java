package com.nimbusrun.logging;

public enum LogLevel {
  INFO("info"), WARN("warn"), ERROR("error"), DEBUG("debug"), UNKNOWN("unknown"), N_A("n/a");
  private String level;

  LogLevel(String level) {
    this.level = level;
  }

  public static LogLevel fromStr(String l) {
    if (l == null) {
      return N_A;
    }
    for (var level : LogLevel.values()) {
      if (level.level.equalsIgnoreCase(l)) {
        return level;
      }
    }
    return UNKNOWN;
  }

  public String getLevel() {
    return level;
  }
}