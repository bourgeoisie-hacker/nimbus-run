package com.nimbusrun.config;

import com.nimbusrun.Constants;
import com.nimbusrun.Utils;
import com.nimbusrun.compute.ActionPool;
import com.nimbusrun.compute.Compute;
import com.nimbusrun.compute.ComputeConfigResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.PropertyUtils;

@Configuration
@Slf4j
public class ConfigReader {

  public static final String NIMBUS_RUN_CONFIGURATION_FILE_ENV = "NIMBUS_RUN_CONFIGURATION_FILE";
  public static final String NIMBUS_RUN_CONFIG_FILE = "nimbus.run.config.file";
  public static final String COMPUTE_KEY = "compute";
  public static final String GITHUB_KEY = "github";
  public static final String NAME_KEY = "name";
  public static final String LOG_LEVEL_KEY = "logLevel";
  public static final String COMPUTE_TYPE = "computeType";
  public static final String GIT_GROUP_NAME_KEY = "groupName";
  public static final String GITHUB_ORGANIZATION_NAME_KEY = "organizationName";
  public static final String GITHUB_TOKEN_KEY = "organizationName";

  @Getter
  private final BaseConfig baseConfig;
  private final Map<String, ActionPool> actionPoolMap = new HashMap<>();

  public ConfigReader(Compute compute) {
    BaseConfig baseConfig1 = null;
    try {

      baseConfig1 = readConfig();
      if (baseConfig1.getCompute() == null) {
        log.error("missing %s configuration".formatted(COMPUTE_KEY));
        System.exit(1);
      }
      ComputeConfigResponse response = null;
      try {
        response = compute.receiveComputeConfigs((Map<String, Object>) baseConfig1.getCompute(),
            baseConfig1.getName());
      } catch (Exception e) {
        Utils.excessiveErrorLog("Failed to process compute configs", e, log);
        System.exit(1);
      }
      if (response.warrnings() != null && !response.warrnings().isEmpty()) {
        response.warrnings().forEach(e -> log.warn("Compute configuration warning: {}", e));
      }
      if (response.errors() != null && !response.errors().isEmpty()) {
        response.errors().forEach(e -> log.error("Compute configuration error: {}", e));
        System.exit(1);
      }
      List<String> validateActionPoolErrors = validateActionPool(response.actionPools());

      if (!validateActionPoolErrors.isEmpty()) {
        log.error("Validation errors for action pools:\n" + String.join("\n\t",
            validateActionPoolErrors));
        System.exit(1);
      }
      actionPoolMap.putAll(response.actionPools().stream()
          .collect(Collectors.toMap(ActionPool::getName, Function.identity())));
    } catch (Exception e) {
      Utils.excessiveErrorLog(
          "Error setting up Configurations occurred due to %s.".formatted(e.getMessage()), e, log);
      System.exit(1);
    }
    this.baseConfig = baseConfig1;
  }


  public static BaseConfig readConfig() throws IOException {
    String configLocation = System.getenv(NIMBUS_RUN_CONFIGURATION_FILE_ENV);

    if (configLocation == null) {
      log.error("Environment Variable %s not set".formatted(NIMBUS_RUN_CONFIGURATION_FILE_ENV));
      System.exit(1);
    }
    if (Files.notExists(Paths.get(configLocation)) || !Files.isRegularFile(
        Paths.get(configLocation))) {
      log.error("Config file {} either does not exist or is not a regular file", configLocation);
      System.exit(1);
    }
    BaseConfig baseConfig = readConfig(configLocation);
    List<String> errors = validateBaseConfig(baseConfig);
    if (!errors.isEmpty()) {
      errors.forEach(log::error);
      System.exit(1);
    }
    return baseConfig;
  }

  public static BaseConfig readConfig(String configPath) throws IOException {
    return createBaseConfig(Files.readString(Paths.get(configPath)));
  }


  private static List<String> validateActionPool(List<ActionPool> actionPools) {
    List<String> errors = new ArrayList<>();
    Map<String, Long> nameHistogram = actionPools.stream()
        .collect(Collectors.groupingBy(ActionPool::getName, Collectors.counting()));
    nameHistogram.keySet().stream().filter(k -> nameHistogram.get(k) > 1)
        .forEach((k) -> errors.add("Duplicate name for %s in action pool".formatted(k)));
    long defaultCount = actionPools.stream().filter(ActionPool::isDefault).count();
    if (defaultCount > 1) {
      errors.add("More than one default action pool");
    }
    actionPools.stream().map(ActionPool::getName)
        .filter(name -> !name.matches(Constants.ACTION_POOL_NAME_REGEX)).forEach(name -> {
          errors.add(
              "Action pool \"%s\" does not match the pattern %s. Names should be lowercase, no spaces, no special, alphanumeric, and dashes/underscores/periods are allowed".formatted(
                  name, Constants.ACTION_POOL_NAME_REGEX));
        });
    return errors;
  }

  public static void notNull(Supplier<Object> supplier, String name, Consumer<String> whatToDo) {
    if (supplier.get() == null) {
      whatToDo.accept(name);
    }
  }

  public static List<String> validateBaseConfig(BaseConfig baseConfig) {
    List<String> errors = new ArrayList<>();
    Consumer<String> addToErrorConsumer = (name) -> errors.add(
        "%s missing configuration".formatted(name));
    notNull(baseConfig::getName, NAME_KEY, addToErrorConsumer);
    notNull(baseConfig::getCompute, COMPUTE_KEY, addToErrorConsumer);
    notNull(baseConfig::getComputeType, COMPUTE_TYPE, addToErrorConsumer);
    notNull(baseConfig::getGithub, GITHUB_KEY, addToErrorConsumer);

    if (baseConfig.getGithub() != null) {
      UnaryOperator<String> combineName = (name) -> GITHUB_KEY + "." + name;
      BaseConfig.GithubConfig g = baseConfig.getGithub();
      notNull(g::getGroupName, combineName.apply(GIT_GROUP_NAME_KEY), addToErrorConsumer);
      notNull(g::getOrganizationName, combineName.apply(GITHUB_ORGANIZATION_NAME_KEY),
          addToErrorConsumer);
      notNull(g::getToken, combineName.apply(GITHUB_TOKEN_KEY), addToErrorConsumer);
    }
    return errors;
  }

  public Map<String, ActionPool> getActionPoolMap() {
    return Collections.unmodifiableMap(this.actionPoolMap);
  }


  public static BaseConfig createBaseConfig(String baseConfigStr) {

    PropertyUtils propertyUtils = new PropertyUtils();
    propertyUtils.setSkipMissingProperties(true);
    LoaderOptions loaderOptions = new LoaderOptions();
    loaderOptions.setEnumCaseSensitive(false);
    Constructor constructor = new Constructor(BaseConfig.class, loaderOptions);
    constructor.setPropertyUtils(propertyUtils);
    Yaml yaml = new Yaml(constructor);

    return yaml.load(baseConfigStr);
  }
}
