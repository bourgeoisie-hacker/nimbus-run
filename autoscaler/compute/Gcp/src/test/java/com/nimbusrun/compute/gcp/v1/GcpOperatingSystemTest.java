package com.nimbusrun.compute.gcp.v1;

import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GcpOperatingSystemTest {

  @Test
  void fromYaml_mapsUbuntu2204() {
    assertEquals(GcpOperatingSystem.UBUNTU_22_04, GcpOperatingSystem.fromYaml("ubuntu22.04"));
    assertEquals(GcpOperatingSystem.UBUNTU_22_04, GcpOperatingSystem.fromYaml("Ubuntu22.04"));
  }

  @Test
  void fromYaml_unknownOnBadValue() {
    assertEquals(GcpOperatingSystem.UNKNOWN, GcpOperatingSystem.fromYaml("freebsd13"));
  }

  @Test
  void createRegex_matchesUbuntu2204Names() {
    String regex = GcpOperatingSystem.UBUNTU_22_04.createRegex();
    assertTrue(Pattern.compile(regex).matcher("ubuntu-2204-jammy-arm64-v20250828").matches());
    assertTrue(Pattern.compile(regex).matcher("ubuntu-2204-lts-x86-64-foo").matches());
  }

  @Test
  void gcpProviderProject_isCorrect() {
    assertEquals("ubuntu-os-cloud", GcpOperatingSystem.UBUNTU_24_04.gcpProviderProject());
    assertEquals("debian-cloud", GcpOperatingSystem.DEBIAN_12.gcpProviderProject());
    assertEquals("", GcpOperatingSystem.UNKNOWN.gcpProviderProject());
  }
}
