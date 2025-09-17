package com.nimbusrun.orm;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)

public class CurrentInstances {

  @JsonIgnore
  private Map<String, List<String>> pools = new LinkedHashMap<>();

  public static void main(String[] args) throws JsonProcessingException {
    String s = """
        { "one":["i-434534543", "i-34534543534"], "two":["i-58uj8t5", "i-435hh3u4534"] }
        """;
    ObjectMapper o = new ObjectMapper();
    System.out.println(o.readValue(s,CurrentInstances.class));
  }
  // Capture any unknown top-level key (e.g., "one", "two", "three", ...)
  @JsonAnySetter
  public void put(String key, List<String> ids) {
    pools.put(key, ids);
  }

  // Serialize map entries back as top-level properties
  @JsonAnyGetter
  public Map<String, List<String>> any() {
    return pools;
  }
}
