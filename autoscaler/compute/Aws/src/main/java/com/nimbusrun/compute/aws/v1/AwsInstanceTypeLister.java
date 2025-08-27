package com.nimbusrun.compute.aws.v1;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.regions.Region;

public class AwsInstanceTypeLister {

    public static void listAllInstanceTypes() {
        Ec2Client ec2 = Ec2Client.builder()
                .region(Region.US_EAST_1) // change to your preferred region
                .build();

        String nextToken = null;

        do {
            DescribeInstanceTypesRequest request = DescribeInstanceTypesRequest.builder()
                    .maxResults(100)
                    .nextToken(nextToken)
                    .build();

            DescribeInstanceTypesResponse response = ec2.describeInstanceTypes(request);

            for (InstanceTypeInfo info : response.instanceTypes()) {
                System.out.printf("Instance Type: %s%n", info.instanceTypeAsString());
                System.out.printf("  vCPUs: %d%n", info.vCpuInfo().defaultVCpus());
                System.out.printf("  Memory (MiB): %d%n", info.memoryInfo().sizeInMiB());
                System.out.printf("  Storage: %s%n", info.instanceStorageInfo() != null
                        ? info.instanceStorageInfo().toString()
                        : "EBS only / no instance storage");
                System.out.printf("  Network Performance: %s%n", info.networkInfo().networkPerformance());
                System.out.println("--------------------------------------------------");
            }

            nextToken = response.nextToken();

        } while (nextToken != null);

        ec2.close();
    }

    public static void main(String[] args) {
        listAllInstanceTypes();
    }
}
