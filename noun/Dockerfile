FROM openjdk:8-jdk
MAINTAINER Deven Phillips <deven.phillips@redhat.com>

WORKDIR /opt
COPY target/*.jar /opt/
COPY docker_config.json /opt/docker_config.json

CMD java -Djgroups.use.jdk_logger=true -jar *.jar --conf docker_config.json
