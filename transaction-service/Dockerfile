FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY docker/maven/settings.xml /root/.m2/settings.xml
COPY transaction-service/pom.xml .
COPY transaction-service/src ./src
ENV MAVEN_OPTS="-Dmaven.wagon.http.retryHandler.count=5"
RUN --mount=type=cache,target=/root/.m2/repository \
    sh -ec 'for n in 1 2 3 4 5; do \
      mvn -B package -Dmaven.test.skip=true \
        -Daether.connector.connectTimeout=120000 \
        -Daether.connector.requestTimeout=600000 && exit 0; \
      echo "Maven failed (attempt $n/5), retrying in 30s..."; sleep 30; \
    done; exit 1'

FROM eclipse-temurin:17-jre
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8085
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
