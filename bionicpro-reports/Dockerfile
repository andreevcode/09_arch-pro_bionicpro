# ---- build stage ----
FROM gradle:9.3.0-jdk25 AS build
WORKDIR /app

# Копируем конфигурацию Gradle и исходники
COPY build.gradle settings.gradle ./
COPY src ./src

RUN gradle --no-daemon clean bootJar

# ---- runtime stage (чистый и легкий JRE) ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# В Gradle собранный jar обычно лежит в build/libs/
# Берем тот, который не содержит слово 'plain' (нам нужен fat-jar со всеми зависимостями)
COPY --from=build /app/build/libs/*.jar /app/bionicpro-reports.jar

EXPOSE 8082
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/bionicpro-reports.jar"]