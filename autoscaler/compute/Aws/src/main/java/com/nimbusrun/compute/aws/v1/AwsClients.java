package com.nimbusrun.compute.aws.v1;

import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;


public class AwsClients {

  private static AwsCredentialsProvider credentialsProvider(Optional<String> credentialsName) {
    if (credentialsName.isPresent()) {
      return ProfileCredentialsProvider.create(credentialsName.get());
    } else {
      return DefaultCredentialsProvider.create();
    }
  }

  public static Ec2Client ec2Client(Optional<String> credentialsName, Region region) {
    AwsCredentialsProvider credentialsProvider = credentialsProvider(credentialsName);

    return Ec2Client.builder()
        .region(region)
        .credentialsProvider(credentialsProvider)
        .build();

  }

}
