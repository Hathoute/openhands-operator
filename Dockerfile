FROM docker.public.nexus.dev.hathoute.com/eclipse-temurin:21.0.7_6-jre

WORKDIR /app
COPY target/openhands-operator.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]