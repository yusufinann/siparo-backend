FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system siparo && useradd --system --gid siparo --home-dir /app siparo
WORKDIR /app
COPY --from=build /build/target/backend-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir /app/uploads && chown siparo:siparo /app/uploads
USER siparo
ENV PORT=8080
# Render Free: 512 MB RAM and a small CPU share. C1-only JIT cuts startup CPU time roughly 4x (measured locally).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Xss512k -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
