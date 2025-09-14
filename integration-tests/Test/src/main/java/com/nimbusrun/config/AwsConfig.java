package com.nimbusrun.config;

import lombok.Data;

@Data
public class AwsConfig {

  private final String region;
  private final String subnet;
  private final String securityGroup;
}
