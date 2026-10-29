FROM mcr.microsoft.com/playwright/java:v1.64.0-noble
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
ENTRYPOINT ["mvn", "-B", "test"]