package com.nimbusrun.config;

import java.util.List;
import lombok.Data;

@Data
public class GcpConfig {

  private final String projectId;
  private final String region;
  private final String subnet;
  private final String vpc;
  private final List<String> zones;

}
