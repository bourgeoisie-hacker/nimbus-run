package com.nimbusrun.compute.gcp.v1;

import com.google.cloud.compute.v1.*;

import java.io.IOException;

public class GcpMachineTypeLister {

    public static void listMachineTypes(String projectId, String zone) throws IOException {
        try (MachineTypesClient machineTypesClient = MachineTypesClient.create(); ZonesClient zonesClient = ZonesClient.create()) {
            ListMachineTypesRequest request = ListMachineTypesRequest.newBuilder()
                    .setProject(projectId)
                    .setZone(zone)
                    .build();

            System.out.printf("Machine types in zone [%s]:%n", zone);
            for (MachineType machineType : machineTypesClient.list(request).iterateAll()) {
                System.out.printf(" - %s (%s vCPU, %s MB RAM)%n",
                        machineType.getName(),
                        machineType.getGuestCpus(),
                        machineType.getMemoryMb());
            }
            // Get CPU platforms supported in this zone
            Zone zoneInfo = zonesClient.get(projectId, zone);
            System.out.println("\nAvailable CPU platforms:");
            for (String platform : zoneInfo.getAvailableCpuPlatformsList()) {
                System.out.println(" - " + platform);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        String projectId = "massive-dynamo-342018";
        String zone = "us-central1-a";
        listMachineTypes(projectId, zone);
    }
}
