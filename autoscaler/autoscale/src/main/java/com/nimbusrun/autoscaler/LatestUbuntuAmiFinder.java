package com.nimbusrun.autoscaler;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Image;

import java.util.Comparator;
import java.util.List;

public class LatestUbuntuAmiFinder {

    public static void main(String[] args) {
        // Replace with your desired AWS Region
        Region region = Region.US_EAST_1; 

        try (Ec2Client ec2Client = Ec2Client.builder()
                .region(region)
                .build()) {

            DescribeImagesRequest request = DescribeImagesRequest.builder()
                    .owners("099720109477") // Canonical's owner ID
                    .filters(
                            Filter.builder()
                                    .name("name")
                                    .values("ubuntu/images/hvm-ssd/ubuntu-*-amd64-server-*") // Adjust for specific Ubuntu version if needed
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
                System.out.println("Latest Ubuntu AMI ID: " + latestAmiId);
            } else {
                System.out.println("No Ubuntu AMIs found matching the criteria.");
            }
        } catch (Exception e) {
            System.err.println("Error finding latest Ubuntu AMI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}