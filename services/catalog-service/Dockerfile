# Build context is the REPO ROOT, not this directory - see infra/docker-compose.yml.
# A module in a Maven reactor cannot be built without its parent POM and the sibling
# modules it depends on, so the context has to span the whole project.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY common ./common
COPY services ./services

# --mount=type=cache keeps ~/.m2 between builds without baking it into a layer, so a
# source change does not re-download the internet. -am builds the modules this one
# depends on (common-web) as part of the same reactor.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl services/catalog-service -am package -DskipTests

# JRE, not JDK: the compiler is build-time only, and leaving it out drops a few
# hundred megabytes from the shipped image.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Never run as root. If something escapes the JVM, it should land on an account that
# owns nothing.
RUN addgroup -S app && adduser -S -G app app

COPY --from=build /build/services/catalog-service/target/catalog-service-*.jar app.jar

# H2 writes here; docker-compose mounts a volume over it so the catalog survives a
# container being replaced.
RUN mkdir -p /app/data && chown -R app:app /app
USER app

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
