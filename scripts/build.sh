set -e
cd ../autoscaler
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

mvn clean
mvn install -Paws -DskipTests
mvn install -Pgcp -DskipTests
mvn jib:build
#TODO need to pull version

cd ../

docker build -t bourgeoisiehacker/autoscaler:latest -f Dockerfile .
docker tag bourgeoisiehacker/autoscaler:latest docker.io/bourgeoisiehacker/autoscaler:$VERSION
docker tag bourgeoisiehacker/autoscaler:latest docker.io/bourgeoisiehacker/autoscaler:latest
docker push  docker.io/bourgeoisiehacker/autoscaler:$VERSION
docker push  docker.io/bourgeoisiehacker/autoscaler:latest

