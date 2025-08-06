package com.nimbusrun.autoscaler.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Data
@Slf4j
public class ConfigurationFileInfo{
    private final List<String> errors;
    private final Path configPath;

    public ConfigurationFileInfo(ApplicationArguments arguments){
        this.errors = new ArrayList<>();
        boolean argsHaveConfig = arguments.containsOption(ConfigReader.NIMBUS_RUN_CONFIG_FILE);

        String configLocation = System.getenv(ConfigReader.NIMBUS_RUN_CONFIGURATION_FILE_ENV);
        Path confirmedConfigPath = null;
        if(argsHaveConfig){
            Path configPath = Path.of(arguments.getOptionValues(ConfigReader.NIMBUS_RUN_CONFIG_FILE).getFirst());
            if(Files.notExists(configPath) || !Files.isRegularFile(configPath)){
                this.errors.add("cli option %s for file %s does not exist or is not a regular file".formatted(ConfigReader.NIMBUS_RUN_CONFIG_FILE, configLocation));
            }else{
                confirmedConfigPath = configPath;
            }
        }else if(configLocation != null){
            Path configPath = Path.of(configLocation);
            if(Files.notExists(configPath) || !Files.isRegularFile(configPath)){
                errors.add("Environment Variable %s for file %s does not exist or is not a regular file".formatted(ConfigReader.NIMBUS_RUN_CONFIGURATION_FILE_ENV, configLocation));
            }else{
                confirmedConfigPath = configPath;
            }
        } else {
            errors.add("Neither cli option %s nor environment variable %s set.".formatted(ConfigReader.NIMBUS_RUN_CONFIG_FILE, ConfigReader.NIMBUS_RUN_CONFIGURATION_FILE_ENV));
        }
        this.configPath = confirmedConfigPath;
    }
}
