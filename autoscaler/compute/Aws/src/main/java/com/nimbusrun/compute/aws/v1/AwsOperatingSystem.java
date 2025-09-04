package com.nimbusrun.compute.aws.v1;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.nimbusrun.compute.OperatingSystem;
import com.nimbusrun.compute.ProcessorArchitecture;

import java.io.IOException;

public enum AwsOperatingSystem {
    UBUNTU_20_04(OperatingSystem.UBUNTU_20_04,"20.04", true),
    UBUNTU_22_04(OperatingSystem.UBUNTU_22_04, "22.04", true),
    UBUNTU_23_04(OperatingSystem.UBUNTU_23_04, "23.04", true),
    UBUNTU_24_04(OperatingSystem.UBUNTU_24_04,  "24.04", true),
    UBUNTU_25_04(OperatingSystem.UBUNTU_25_04,"25.04", true),
    DEBIAN_11(OperatingSystem.DEBIAN_11,"11", true),
    DEBIAN_12(OperatingSystem.DEBIAN_12,"12", true),
    DEBIAN_13(OperatingSystem.DEBIAN_13,"13", false),
    UNKNOWN(OperatingSystem.UNKNOWN, "", false);

    private final OperatingSystem operatingSystem;
    private final String version;
    private final boolean available;

    AwsOperatingSystem(OperatingSystem operatingSystem,  String version, boolean available){
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

    public static String determineArchValue(ProcessorArchitecture architecture){
        String type = "amd64";
        if(architecture.getType().equalsIgnoreCase("arm64")){
            type = "arm64";
        }
        return type;
    }
    public String createRegex(){
        return switch (this){
            case UBUNTU_20_04, UBUNTU_22_04,UBUNTU_23_04, UBUNTU_24_04, UBUNTU_25_04 -> "ubuntu/images/hvm-ssd*/ubuntu-*-%s-*-server-*".formatted(this.getVersion());
            case DEBIAN_11, DEBIAN_12, DEBIAN_13 ->"debian-%s-*".formatted(this.version);
            case UNKNOWN -> "";
        };
    }
    public String gcpProviderProject(){
        return switch (this){
            case UBUNTU_20_04, UBUNTU_22_04,UBUNTU_23_04, UBUNTU_24_04, UBUNTU_25_04 -> "099720109477";
            case DEBIAN_11, DEBIAN_12, DEBIAN_13 -> "136693071363";
            case UNKNOWN -> "";
        };
    }

    public static AwsOperatingSystem fromYaml(String osStr){
        for(AwsOperatingSystem os: AwsOperatingSystem.values()){
            if(os.operatingSystem.getShortName().equalsIgnoreCase(osStr)){
                return os;
            }
        }
        return UNKNOWN;
    }
    public static class Deserialize extends JsonDeserializer<AwsOperatingSystem> {

        @Override
        public AwsOperatingSystem deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
            String dateString = jsonParser.getText(); // Assuming date is a simple string in JSON
            return AwsOperatingSystem.fromYaml(dateString);
        }
    }
}
