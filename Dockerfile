FROM eclipse-temurin:17-jre

WORKDIR /app
COPY build/libs/*-0.1.0.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]
