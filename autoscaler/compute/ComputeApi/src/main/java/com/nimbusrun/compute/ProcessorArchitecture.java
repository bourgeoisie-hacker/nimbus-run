package com.nimbusrun.compute;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public enum ProcessorArchitecture {
  ARM64("arm64"), X64("x64"), UNKNOWN("unknown");


  private String type;

  ProcessorArchitecture(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }

  public static ProcessorArchitecture fromYaml(String osStr) {
    for (ProcessorArchitecture os : ProcessorArchitecture.values()) {
      if (os.type.equalsIgnoreCase(osStr)) {
        return os;
      }
    }
    return UNKNOWN;
  }

  public static class Deserialize extends JsonDeserializer<ProcessorArchitecture> {

    @Override
    public ProcessorArchitecture deserialize(JsonParser jsonParser,
        DeserializationContext deserializationContext) throws IOException, JacksonException {
      String dateString = jsonParser.getText(); // Assuming date is a simple string in JSON
      return ProcessorArchitecture.fromYaml(dateString);
    }
  }
}
