package com.nimbusrun.compute.gcp.v1;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.compute.v1.*;

import java.io.FileInputStream;
import java.io.IOException;


public class GCPClients {

    public static InstancesClient createInstancesClient(GCPConfig.ActionPool actionPool) throws IOException {
        if(actionPool.getServiceAccountPathOpt().isPresent()){
            GoogleCredentials credentials = ServiceAccountCredentials
                    .fromStream(new FileInputStream(actionPool.getServiceAccountPath()));

            InstancesSettings settings = InstancesSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            return InstancesClient.create(settings);
        }
        return InstancesClient.create();
    }

    public static ZonesClient createZonesClient(GCPConfig.ActionPool actionPool) throws IOException {
        if(actionPool.getServiceAccountPathOpt().isPresent()){
            GoogleCredentials credentials = ServiceAccountCredentials
                    .fromStream(new FileInputStream(actionPool.getServiceAccountPath()));
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
    public static MachineTypesClient createMachineTypesClient(GCPConfig.ActionPool actionPool) throws IOException {
        if(actionPool.getServiceAccountPathOpt().isPresent()){
            GoogleCredentials credentials = ServiceAccountCredentials
                    .fromStream(new FileInputStream(actionPool.getServiceAccountPath()));
            MachineTypesSettings settings = MachineTypesSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            return MachineTypesClient.create(settings);
        }
        return MachineTypesClient.create();
    }

}
