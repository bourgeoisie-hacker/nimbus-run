package com.nimbusrun.autoscaler;

import com.google.cloud.compute.v1.Region;
import com.google.cloud.compute.v1.RegionsClient;
import com.google.cloud.compute.v1.Zone;
import com.google.cloud.compute.v1.ZonesClient;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Test {
    public static void main(String[] args) throws IOException {
        Map<String,String> map = new HashMap<>();
        map.put("blah", null);
        System.out.println(map.containsKey("blah"));
        listRegions("massive-dynamo-342018");
    }

    public static void listRegions(String projectId) throws IOException {

        try (ZonesClient zonesClient = ZonesClient.create()) {
            for (Zone zone : zonesClient.list(projectId).iterateAll()) {
                String regionName = zone.getRegion().substring(zone.getRegion().lastIndexOf("/")+1);
                System.out.printf("Region: %s, Zone: %s (status: %s)%n", regionName, zone.getName() , zone.getStatus());
            }
        }
    }
}
