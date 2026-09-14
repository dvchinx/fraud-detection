FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# Capa de dependencias separada: solo se invalida si cambia el pom.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre

# curl para el healthcheck de docker-compose contra /actuator/health.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 spring

WORKDIR /app
COPY --from=build /build/target/backend-*.jar app.jar

USER spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
