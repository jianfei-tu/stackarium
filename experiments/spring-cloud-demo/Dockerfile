FROM eclipse-temurin:17-jre-alpine
ARG MODULE
WORKDIR /app
COPY ${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
