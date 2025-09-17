package com.nimbusrun.config;

import java.util.List;
import lombok.Data;

@Data
public class AwsConfig {

  private final String region;
  private final String subnet;
  private final List<String> securityGroup;
}
