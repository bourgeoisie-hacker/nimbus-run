package com.nimbusrun.compute.aws.v1.junk;

import com.nimbusrun.compute.aws.v1.AwsConfig;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class YamlLoader {

  static String item = """
      defaultSettings:
        idleScaleDownInMinutes: 2
        region: us-east-1
        subnet: subnet-257dbf7d
        securityGroups: sg-0189c3298c7be64ca
        #    credentialsProfile: #<------- if blank it'll just use the default Credentials provider
        diskSettings:
          type: "gp3" #Possible values gp3 | gp2 | io2 | io1| st1
          size: "20" # in gigs
        instanceType: t3.medium
        maxInstanceCount: 10 # <--no max
        keyPairName: keypair
      defaultActionPool:
        name: defaultPool
      actionPools:
        - name: t3a.xlarge
          instanceType: t3a.xlarge
          maxInstanceCount: 1
          subnet: subnet-1234
          securityGroups: ["securityGroup-1234"]
          diskSettings:
            type: "gp2"
            size: 4
        - instanceType: c4.2xlarge
          maxInstanceCount: 4
          name: c4.2xlarge
        - instanceType: m8gd.medium
          name: m8gd.medium
          maxInstanceCount: 3
          isNvme: true
        - instanceType: t3.medium
          name: t3.medium
          maxInstanceCount: 3
        - name: haha
      """;

  public static void main(String[] args) throws Exception {
    Yaml yaml = new Yaml(new Constructor(AwsConfig.class, new LoaderOptions()));

//            GCPConfig config = yaml.load(item);
//            config.fillInActionPoolWithDefaults();
//            System.out.println("Loaded config: " + config);
    AwsConfig config = yaml.load(item);
    config.fillInActionPoolWithDefaults();
    System.out.println("Loaded config: " + config);

  }
}
