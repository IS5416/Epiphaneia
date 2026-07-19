FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY pom.xml ./
COPY epiphaneia-infra/pom.xml epiphaneia-infra/
COPY epiphaneia-connector/pom.xml epiphaneia-connector/
COPY epiphaneia-engine/pom.xml epiphaneia-engine/
COPY epiphaneia-agent-core/pom.xml epiphaneia-agent-core/
COPY epiphaneia-server/pom.xml epiphaneia-server/
RUN ./mvnw dependency:go-offline -pl epiphaneia-server -am
COPY . .
RUN ./mvnw package -DskipTests -pl epiphaneia-server -am

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache curl
RUN addgroup -S epiphaneia && adduser -S epiphaneia -G epiphaneia
WORKDIR /app
COPY --from=builder /build/epiphaneia-server/target/epiphaneia-server-*.jar app.jar
USER epiphaneia
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75", \
    "-XX:+UseG1GC", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/tmp", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
