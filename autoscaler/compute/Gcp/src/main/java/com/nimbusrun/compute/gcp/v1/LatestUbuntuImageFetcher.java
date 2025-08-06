package com.nimbusrun.compute.gcp.v1;

import com.google.cloud.compute.v1.Image;
import com.google.cloud.compute.v1.ImagesClient;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.ListImagesRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Optional;

public class LatestUbuntuImageFetcher {


    public static void main(String[] args) throws IOException {
        String project = "ubuntu-os-cloud";

        try (ImagesClient imagesClient = ImagesClient.create(); InstancesClient instancesClient = InstancesClient.create()) {
            ListImagesRequest request = ListImagesRequest.newBuilder()
                    .setProject(project)
                    .setMaxResults(1)

                    .setOrderBy("creationTimestamp desc")  // newest first
//                    .setFilter("name eq ^ubuntu.*$")
                    .build();
//            instancesClient.list("massive-dynamo-342018", "us-east-1");
            Iterator<Image> latestImageIterator = imagesClient.list(request)
                    .iterateAll()
                    .iterator();
            Image latest = null;
            while(latestImageIterator.hasNext()) {
                Image latestImage = latestImageIterator.next();
                if("X86_64".equalsIgnoreCase(latestImage.getArchitecture()) && latestImage.hasCreationTimestamp() && (latest == null || Instant.parse(latestImage.getCreationTimestamp()).isAfter(Instant.parse(latest.getCreationTimestamp())))){
                    latest = latestImage;
                }
            }
            System.out.println("Latest Ubuntu Image:");
            System.out.println(" - Name: " + latest.getName());
            System.out.println(" - Self Link: " + latest.getSelfLink());
            System.out.println(" - Family: " + latest.getFamily());
            System.out.println(" - Creation Time: " + latest.getCreationTimestamp());
        }
    }

}
