package com.nimbusrun.compute.gcp.v1;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class GCPConfigTest {

  // Minimal compute section modeled after your config-gcp.yaml (only the fields we assert)
  private static final String COMPUTE_YAML = ""
      + "defaultSettings:\n"
      + "  projectId: my-super-project\n"
      + "  region: us-east1\n"
      + "  subnet: regions/us-east1/subnetworks/default\n"
      + "  vpc: global/networks/default\n"
      + "  zones: [us-east1-b, us-east1-c, us-east1-d]\n"
      + "  diskSettings:\n"
      + "    size: 20\n"
      + "  instanceType: e2-highcpu-4\n"
      + "  maxInstanceCount: 10\n"
      + "  os: ubuntu22.04\n"
      + "  architecture: x64\n"
      + "defaultActionPool:\n"
      + "  name: default-pool\n"
      + "actionPools:\n"
      + "  - name: n2d-standard-2\n"
      + "    instanceType: n2d-standard-2\n"
      + "    maxInstanceCount: 1\n"
      + "    subnet: subnet-1234\n"
      + "    diskSettings:\n"
      + "      size: 12\n"
      + "  - name: n1-standard-4\n"
      + "    instanceType: n1-standard-4\n"
      + "    maxInstanceCount: 4\n"
      + "  - name: c4-standard-4-lssd\n"
      + "    instanceType: c4-standard-4-lssd\n"
      + "    maxInstanceCount: 3\n"
      + "  - name: e2-highcpu-4\n"
      + "    instanceType: e2-highcpu-4\n"
      + "    maxInstanceCount: 3\n";

  @Test
  void createGcpConfigs_fillsDefaultsIntoPools() {
    // In production this "map" is the 'compute' section. We'll mimic that.
    Map<String, Object> computeMap = new Yaml().load(COMPUTE_YAML);

    // Map -> JSON -> GCPConfig + fill defaults
    GCPConfig cfg = new GCPConfig().createGcpConfigs(computeMap);

    // defaultSettings basic assertions
    GCPConfig.ActionPool defaults = cfg.getDefaultSettings();
    assertEquals("my-super-project", defaults.getProjectId());
    assertEquals("us-east1", defaults.getRegion());
    assertEquals("regions/us-east1/subnetworks/default", defaults.getSubnet());
    assertEquals("global/networks/default", defaults.getVpc());
    assertEquals(List.of("us-east1-b", "us-east1-c", "us-east1-d"), defaults.getZones());
    assertNotNull(defaults.getDiskSettings());
    assertEquals(20, defaults.getDiskSettings().getSize());
    assertEquals("e2-highcpu-4", defaults.getInstanceType());
    assertEquals(10, defaults.getMaxInstanceCount());
    assertEquals(GcpOperatingSystem.UBUNTU_22_04, defaults.getOs());
    // architecture string is deserialized by your custom deserializer
    assertNotNull(defaults.getArchitecture());

    // defaultActionPool inherits defaults and is marked default
    GCPConfig.ActionPool dap = cfg.getDefaultActionPool();
    assertEquals("default-pool", dap.getName());
    assertTrue(dap.isDefault());
    assertEquals("my-super-project", dap.getProjectId());
    assertEquals("us-east1", dap.getRegion());
    assertEquals("global/networks/default", dap.getVpc());
    assertEquals(20, dap.getDiskSettings().getSize());
    assertEquals("e2-highcpu-4", dap.getInstanceType());

    // actionPools exist and inherit what they don't override
    assertEquals(4, cfg.getActionPools().size());
    GCPConfig.ActionPool n1 = cfg.getActionPools().stream()
        .filter(p -> p.getName().equals("n1-standard-4")).findFirst().orElseThrow();
    assertEquals("my-super-project", n1.getProjectId());
    assertEquals("regions/us-east1/subnetworks/default", n1.getSubnet()); // inherited
    assertEquals("n1-standard-4", n1.getInstanceType());
    assertEquals(4, n1.getMaxInstanceCount());

    GCPConfig.ActionPool n2d = cfg.getActionPools().stream()
        .filter(p -> p.getName().equals("n2d-standard-2")).findFirst().orElseThrow();
    assertEquals("subnet-1234", n2d.getSubnet()); // overridden
    assertEquals(12, n2d.getDiskSettings().getSize()); // overridden

    GCPConfig.ActionPool c4 = cfg.getActionPools().stream()
        .filter(p -> p.getName().equals("c4-standard-4-lssd")).findFirst().orElseThrow();
  }
}
