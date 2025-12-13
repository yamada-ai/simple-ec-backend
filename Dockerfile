# syntax=docker/dockerfile:1

# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts settings.gradle.kts ./
COPY src src
COPY docs docs
COPY compose.yaml compose.yaml
COPY docker docker
ARG USE_PREBUILT_JAR=true
ARG BOOTJAR_GLOB=build/libs/*.jar
ENV SPRING_PROFILES_ACTIVE=docker
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/simple_ec
ENV SPRING_DATASOURCE_USERNAME=postgres
ENV SPRING_DATASOURCE_PASSWORD=postgres

# すでに build/libs/*.jar がある場合はそれを使い、無ければビルドする
RUN if [ "$USE_PREBUILT_JAR" = "true" ] && compgen -G "$BOOTJAR_GLOB" > /dev/null; then \
      echo "Using prebuilt bootJar: $BOOTJAR_GLOB"; \
      cp $BOOTJAR_GLOB /app/app.jar; \
    else \
      ./gradlew clean bootJar -x test -x flywayMigrate -x generateJooq; \
      cp build/libs/*.jar /app/app.jar; \
    fi

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
# build stage で /app/app.jar にコピー済みなのでそのまま持ってくる
COPY --from=build /app/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
