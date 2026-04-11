FROM eclipse-temurin:17-jre
WORKDIR /app
COPY ticket-service/build/libs/ticket-service-0.0.1-SNAPSHOT.jar /app/ticket-service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/ticket-service.jar"]
