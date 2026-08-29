FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
COPY config ./config
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 smartparking \
    && useradd --system --uid 10001 --gid smartparking --no-create-home smartparking

WORKDIR /app
COPY --from=build /workspace/target/smart-parking-backend-*.jar app.jar

USER smartparking
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
