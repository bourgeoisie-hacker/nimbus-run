package com.nimbusrun.compute.gcp.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public class Main {

  public static String f = """
      
      defaultSettings:
        os: ubuntu20.04
        architecture: x64
        idleScaleDownInMinutes: 10
        projectId: massive-dynamo-342018
        region: us-east1
        subnet: regions/us-east1/subnetworks/default
        vpc: global/networks/default
        zones: ["us-east1-b","us-east1-c", "us-east1-d"]
      #    serviceAccountPath: #<------- if blank it'll just use the default Credentials provider
        diskSettings:
          size: "20" # in gigs
        instanceType: e2-highcpu-4
        maxInstanceCount: 10 # <--no max
      defaultActionPool:
        name: default-pool
      actionPools:
        - name: test
          instanceType: n2d-standard-2
          maxInstanceCount: 1
          subnet: subnet-1234
          diskSettings:
            size: 12
        - instanceType: n1-standard-4
          maxInstanceCount: 4
          name: n1-standard-4
        - instanceType: c4-standard-4-lssd
          name: c4-standard-4-lssd
          maxInstanceCount: 3
          isNvme: true
        - instanceType: e2-highcpu-4
          name: e2-highcpu-4
          maxInstanceCount: 3
      
      """;

  public static void main(String[] args) throws JsonProcessingException {
    Yaml yaml = new Yaml();
    Map<String, Object> yamlMap = yaml.load(f);
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    String jsonString = objectMapper.writeValueAsString(yamlMap);
    GCPConfig c = objectMapper.readValue(jsonString, GCPConfig.class);
    System.out.println(jsonString);
  }
}
