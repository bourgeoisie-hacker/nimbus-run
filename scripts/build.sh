set -e
cd ../autoscaler
mvn clean
mvn install -Paws -DskipTests
mvn install -Pgcp -DskipTests
mvn jib:dockerBuild
#TODO need to pull version
cd ../

docker build -t bourgeoisiehacker/autoscaler:latest -f Dockerfile .
#podman tag bourgeoisiehacker/autoscaler:latest docker.io/bourgeoisiehacker/autoscaler:1.0.0
#podman push  docker.io/bourgeoisiehacker/autoscaler:1.0.0

