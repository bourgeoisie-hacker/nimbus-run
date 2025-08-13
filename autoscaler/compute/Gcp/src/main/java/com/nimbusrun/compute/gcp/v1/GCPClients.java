package com.nimbusrun.compute.gcp.v1;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.compute.v1.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;


public class GCPClients {

    public static InstancesClient createInstancesClient(@Nonnull Optional<String> serviceAccountPath) throws IOException {
        if(serviceAccountPath.isPresent()){
            GoogleCredentials credentials = ServiceAccountCredentials
                    .fromStream(new FileInputStream(serviceAccountPath.get()));

            InstancesSettings settings = InstancesSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            return InstancesClient.create(settings);
        }
        return InstancesClient.create();
    }

    public static ZonesClient createZonesClient(@Nonnull Optional<String> serviceAccountPath) throws IOException {
        if(serviceAccountPath.isPresent()){
            GoogleCredentials credentials = ServiceAccountCredentials
                    .fromStream(new FileInputStream(serviceAccountPath.get()));
            ZonesSettings settings = ZonesSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            return ZonesClient.create(settings);
        }
        return ZonesClient.create();
    }

    public static ImagesClient createImagesClient(GCPConfig.ActionPool actionPool) throws IOException {
        if(actionPool.getServiceAccountPathOpt().isPresent()){
            GoogleCredentials credentials = ServiceAccountCredentials
                    .fromStream(new FileInputStream(actionPool.getServiceAccountPath()));
            ImagesSettings settings = ImagesSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            return ImagesClient.create(settings);
        }
        return ImagesClient.create();
    }
    public static MachineTypesClient createMachineTypesClient(@Nonnull Optional<String> serviceAccountPath) throws IOException {
        if(serviceAccountPath.isPresent()){
            GoogleCredentials credentials = ServiceAccountCredentials
                    .fromStream(new FileInputStream(serviceAccountPath.get()));
            MachineTypesSettings settings = MachineTypesSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            return MachineTypesClient.create(settings);
        }
        return MachineTypesClient.create();
    }

}
