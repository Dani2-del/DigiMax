FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY Pagos/ ./Pagos/
RUN mvn -f Pagos/pom.xml --no-transfer-progress package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/Pagos/target/*.jar /app/app.jar
USER 10001:10001
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
