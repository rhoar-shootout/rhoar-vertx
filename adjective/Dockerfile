FROM openjdk:8-jdk
MAINTAINER Deven Phillips <deven.phillips@redhat.com>

WORKDIR /opt
COPY target/*.jar /opt/
COPY default_config.json /opt/default_config.json

CMD java -Djgroups.use.jdk_logger=true -jar *.jar --conf default_config.json