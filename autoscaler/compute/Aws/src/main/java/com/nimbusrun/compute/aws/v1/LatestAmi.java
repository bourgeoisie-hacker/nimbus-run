package com.nimbusrun.compute.aws.v1;

import com.nimbusrun.Utils;
import com.nimbusrun.compute.OperatingSystem;
import com.nimbusrun.compute.ProcessorArchitecture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Image;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;


public class LatestAmi {
    public static void main(String[] args) {
        latestAmi(Ec2Client.builder().region(Region.US_EAST_1).build(), ProcessorArchitecture.X64, AwsOperatingSystem.UBUNTU_23_04);
    }
    static Logger log = LoggerFactory.getLogger(LatestAmi.class);

    public static String determineArch(ProcessorArchitecture architecture){
        String type = "x86_64";
        if(architecture.getType().equalsIgnoreCase("arm64")){
            type = "arm64";
        }
        return type;
    }
    public static String determineArchValue(ProcessorArchitecture architecture){
        String type = "amd64";
        if(architecture.getType().equalsIgnoreCase("arm64")){
            type = "arm64";
        }
        return type;
    }
    public static Optional<String> latestAmi(Ec2Client ec2Client, ProcessorArchitecture architecture, AwsOperatingSystem os){

        try  {
            DescribeImagesRequest request = DescribeImagesRequest.builder()
                    .owners(os.gcpProviderProject()) // Canonical's owner ID. Company managing the images.
                    .filters(
                            Filter.builder()
                                    .name("name")
                                    .values(os.createRegex()) // Adjust for specific Ubuntu version if needed
                                    .build(),
                            Filter.builder()
                                    .name("architecture")
                                    .values(determineArch(architecture))
                                    .build(),
                            Filter.builder()
                                    .name("state")
                                    .values("available")
                                    .build()
                    )
                    .build();

            List<Image> imagesUn = ec2Client.describeImages(request).images();

            // Sort images by creation date in descending order to get the latest
            List<Image> images = imagesUn.stream()
                    .sorted(Comparator.comparing(Image::creationDate).reversed())
                    .toList();

            if (!images.isEmpty()) {
                String latestAmiId = images.get(0).imageId();
                log.debug("Latest Ubuntu AMI ID: " + latestAmiId);
                return Optional.of(latestAmiId);
            } else {
                log.warn("No Ubuntu AMIs found matching the criteria.");
            }
        } catch (Exception e) {
            Utils.excessiveErrorLog("Error finding latest Ubuntu AMI due to %s".formatted(e.getMessage()), e, log);

        }
        return Optional.empty();
    }
}
