package com.nimbusrun.compute;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public enum OperatingSystem {
  UBUNTU_20_04("ubuntu20.04", OperatingSystemFamily.UBUNTU),
  UBUNTU_22_04("ubuntu22.04", OperatingSystemFamily.UBUNTU),
  UBUNTU_23_04("ubuntu23.04", OperatingSystemFamily.UBUNTU),
  UBUNTU_24_04("ubuntu24.04", OperatingSystemFamily.UBUNTU),
  UBUNTU_25_04("ubuntu25.04", OperatingSystemFamily.UBUNTU),

  DEBIAN_11("debian11", OperatingSystemFamily.DEBIAN),
  DEBIAN_12("debian12", OperatingSystemFamily.DEBIAN),
  DEBIAN_13("debian13", OperatingSystemFamily.DEBIAN),

  UNKNOWN("unknown", OperatingSystemFamily.UNKNOWN);

  private String shortName;
  private OperatingSystemFamily family;

  OperatingSystem(String shortName, OperatingSystemFamily family) {
    this.shortName = shortName;
    this.family = family;
  }

  public String getShortName() {
    return shortName;
  }

  public OperatingSystemFamily getFamily() {
    return family;
  }

  public static OperatingSystem fromYaml(String osStr) {
    for (OperatingSystem os : OperatingSystem.values()) {
      if (os.shortName.equalsIgnoreCase(osStr)) {
        return os;
      }
    }
    return UNKNOWN;
  }

  public static class Deserialize extends JsonDeserializer<OperatingSystem> {

    @Override
    public OperatingSystem deserialize(JsonParser jsonParser,
        DeserializationContext deserializationContext) throws IOException, JacksonException {
      String dateString = jsonParser.getText(); // Assuming date is a simple string in JSON
      return OperatingSystem.fromYaml(dateString);
    }
  }
}
