package com.nimbusrun.compute.gcp.v1;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.nimbusrun.compute.OperatingSystem;
import java.io.IOException;

public enum GcpOperatingSystem {
  UBUNTU_20_04(OperatingSystem.UBUNTU_20_04, "2004", true),
  UBUNTU_22_04(OperatingSystem.UBUNTU_22_04, "2204", true),
  UBUNTU_23_04(OperatingSystem.UBUNTU_23_04, "2304", false),
  UBUNTU_24_04(OperatingSystem.UBUNTU_24_04, "2404", true),
  UBUNTU_25_04(OperatingSystem.UBUNTU_25_04, "2504", true),
  DEBIAN_11(OperatingSystem.DEBIAN_11, "11", true),
  DEBIAN_12(OperatingSystem.DEBIAN_12, "12", true),
  DEBIAN_13(OperatingSystem.DEBIAN_13, "13", false),
  UNKNOWN(OperatingSystem.UNKNOWN, "", true);

  private final OperatingSystem operatingSystem;
  private final String version;
  private final boolean available;

  GcpOperatingSystem(OperatingSystem operatingSystem, String version, boolean available) {
    this.operatingSystem = operatingSystem;
    this.version = version;
    this.available = available;
  }

  public OperatingSystem getOperatingSystem() {
    return operatingSystem;
  }

  public String getVersion() {
    return version;
  }

  public boolean isAvailable() {
    return available;
  }

  public String createRegex() {
    return switch (this) {
      case UBUNTU_20_04, UBUNTU_22_04, UBUNTU_23_04, UBUNTU_24_04, UBUNTU_25_04 ->
          "^(ubuntu).*?(%s).*".formatted(this.version);
      case DEBIAN_11, DEBIAN_12, DEBIAN_13 -> "^(debian)-(%s).*".formatted(this.version);
      case UNKNOWN -> "";
    };
  }

  public String gcpProviderProject() {
    return switch (this) {
      case UBUNTU_20_04, UBUNTU_22_04, UBUNTU_23_04, UBUNTU_24_04, UBUNTU_25_04 ->
          "ubuntu-os-cloud";
      case DEBIAN_11, DEBIAN_12, DEBIAN_13 -> "debian-cloud";
      case UNKNOWN -> "";
    };
  }

  public static GcpOperatingSystem fromYaml(String osStr) {
    for (GcpOperatingSystem os : GcpOperatingSystem.values()) {
      if (os.operatingSystem.getShortName().equalsIgnoreCase(osStr)) {
        return os;
      }
    }
    return UNKNOWN;
  }

  public static class Deserialize extends JsonDeserializer<GcpOperatingSystem> {

    @Override
    public GcpOperatingSystem deserialize(JsonParser jsonParser,
        DeserializationContext deserializationContext) throws IOException, JacksonException {
      String dateString = jsonParser.getText(); // Assuming date is a simple string in JSON
      return GcpOperatingSystem.fromYaml(dateString);
    }
  }
  public static class Serializer extends JsonSerializer<GcpOperatingSystem> {

    @Override
    public void serialize(GcpOperatingSystem gcpOperatingSystem, JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider) throws IOException {
      jsonGenerator.writeString(gcpOperatingSystem.getOperatingSystem().getShortName());
    }
  }
}
