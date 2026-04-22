FROM eclipse-temurin:17-jre
WORKDIR /app
COPY bank-service/build/libs/bank-service-0.0.1-SNAPSHOT.jar /app/bank-service.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/bank-service.jar"]
