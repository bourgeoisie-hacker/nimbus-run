package com.nimbusrun;

import com.nimbusrun.config.BaseConfig;
import com.nimbusrun.config.ConfigReader;
import com.nimbusrun.logging.LogLevel;
import java.io.IOException;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@Slf4j
@SpringBootApplication
@ComponentScan({"com.nimbusrun"})
public class NimbusRunApplication {

  public static void main(String[] args) throws Exception {

    setSystemProperties();
    SpringApplication.run(NimbusRunApplication.class, args);
  }

  public static void setSystemProperties() throws IOException {

    BaseConfig config = ConfigReader.readConfig();
    setLogLevel(config);
    setManagementSettings();
    System.setProperty("spring.config.location",
        System.getenv(ConfigReader.NIMBUS_RUN_CONFIGURATION_FILE_ENV));
    if (Boolean.parseBoolean(config.getStandalone())) {
      System.setProperty("spring.profiles.active", Constants.STANDALONE_PROFILE_NAME);
    }
    System.setProperty("spring.application.name", config.getName());

  }

  public static void setManagementSettings() {
    System.setProperty("management.endpoints.web.path-mapping.prometheus", "metrics");
    System.setProperty("management.endpoints.web.base-path", "/");
    System.setProperty("management.endpoints.web.exposure.include", "prometheus");
  }

  public static void setLogLevel(BaseConfig config) {
    String applicationLogLevels = "info";
    if (config.getLogLevel() != null && config.getLogLevel() != LogLevel.N_A) {
      if (config.getLogLevel() == LogLevel.UNKNOWN) {
        String logLevels = String.join("| ",
            Arrays.stream(LogLevel.values()).map(LogLevel::getLevel).toList());
        log.warn("log level improperly set. Please use values {}", logLevels);
      } else {
        applicationLogLevels = config.getLogLevel().getLevel();
      }

    }
    System.setProperty("logging.level.com.nimbusrun", applicationLogLevels);
  }

}
