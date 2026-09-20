# Build context is the REPO ROOT, not this directory - see infra/docker-compose.yml.
# A module in a Maven reactor cannot be built without its parent POM and the sibling
# modules it depends on, so the context has to span the whole project.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY common ./common
COPY services ./services

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl services/customer-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app

COPY --from=build /build/services/customer-service/target/customer-service-*.jar app.jar

RUN mkdir -p /app/data && chown -R app:app /app
USER app

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
