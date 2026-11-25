# ADR-004 / ACC-23 — CLI Java standalone, FORA do backend.
FROM maven:3.9.10-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/target/hive-retention-job-1.0.0-SNAPSHOT.jar /app/job.jar
COPY --from=build /src/target/dependency /app/dependency
ENTRYPOINT ["java", "-cp", "/app/job.jar:/app/dependency/*", "br.com.systec.hive.retention.Main"]
