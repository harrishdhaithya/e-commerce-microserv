# Build context is the REPO ROOT - see infra/docker-compose.yml. A Maven reactor
# module cannot compile without its parent POM.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY common ./common
COPY services ./services

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl services/api-gateway -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app

COPY --from=build /build/services/api-gateway/target/api-gateway-*.jar app.jar

# No data volume: the gateway is stateless and owns no database.
RUN chown -R app:app /app
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
