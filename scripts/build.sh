cd ../AutoScaler2
mvn clean
mvn package -Paws -DskipTests
mvn package -Pgcp -DskipTests
mvn jib:dockerBuild
