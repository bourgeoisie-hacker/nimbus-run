package com.nimbusrun.compute.gcp.v1;

import com.google.cloud.compute.v1.Image;
import com.google.cloud.compute.v1.ImagesClient;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.ListImagesRequest;
import com.nimbusrun.compute.ProcessorArchitecture;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;

public class LatestUbuntuImageFetcher {

  public static String determineArch(ProcessorArchitecture architecture) {
    String arch = "X86_64";
    if (architecture == ProcessorArchitecture.ARM64) {
      arch = "ARM64";
    }
    return arch;
  }

  public static String findLatestImage(ProcessorArchitecture architecture, GcpOperatingSystem os) {
    String project = os.gcpProviderProject();
    String arch = determineArch(architecture);
    String templ = os.createRegex();
    try (ImagesClient imagesClient = ImagesClient.create()) {
      ListImagesRequest request = ListImagesRequest.newBuilder()
          .setProject(project)
          .setMaxResults(10)
          .setOrderBy("creationTimestamp desc")  // newest first
          .build();
      Iterator<Image> latestImageIterator = imagesClient.list(request)
          .iterateAll()
          .iterator();
      while (latestImageIterator.hasNext()) {
        Image latestImage = latestImageIterator.next();
        if (arch.equalsIgnoreCase(latestImage.getArchitecture())
            && latestImage.hasCreationTimestamp() && latestImage.getName().matches(templ)) {
          return "projects/%s/global/images/%s".formatted(project, latestImage.getName());
        }
      }

    } catch (IOException e) {
//            Utils.excessiveErrorLog("Failed to query for latest image for action pool %s due to %s".formatted(actionPool.getName(), e.getMessage()), e, log);
    }
    return null;
  }

  public static void main(String[] args) throws IOException {

//        latestUbuntuImage(ProcessorArchitecture.X64, OperatingSystem.UBUNTU_24_04);
    String project = "debian-cloud";

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
      while (latestImageIterator.hasNext()) {
        Image latestImage = latestImageIterator.next();
        if (!"X86_64".equalsIgnoreCase(latestImage.getArchitecture())
            && latestImage.hasCreationTimestamp() && (latest == null || Instant.parse(
                latestImage.getCreationTimestamp())
            .isAfter(Instant.parse(latest.getCreationTimestamp())))) {
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
