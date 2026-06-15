# ---- build stage: compile + package inside a Gradle/JDK21 image (no host JDK needed) ----
FROM gradle:jdk21 AS build
WORKDIR /home/gradle/src
# Cache dependency resolution as its own layer
COPY --chown=gradle:gradle build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon || true
COPY --chown=gradle:gradle src ./src
RUN gradle bootJar --no-daemon -x test

# ---- runtime stage: slim JRE, non-root ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
