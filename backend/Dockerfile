# Build stage
FROM maven:3.8.5-openjdk-17-slim AS build
WORKDIR /app

# Copy pom.xml and download dependencies (caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -B

# Runtime stage
FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu
WORKDIR /app

# Install git, ca-certificates and other tools
RUN apt-get update && \
    apt-get install -y --no-install-recommends git ca-certificates curl && \
    rm -rf /var/lib/apt/lists/*

# Install Trivy
RUN curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin

# Pre-download Trivy DBs
RUN mkdir -p /app/trivy-cache && \
    trivy --cache-dir /app/trivy-cache image --download-db-only && \
    trivy --cache-dir /app/trivy-cache image --download-java-db-only

# Copy Docker CLI binary from official image (connects to DinD daemon via DOCKER_HOST env var)
COPY --from=docker:27-cli /usr/local/bin/docker /usr/local/bin/docker

# Install Helm
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 && \
    chmod 700 get_helm.sh && \
    ./get_helm.sh && \
    rm get_helm.sh

# Copy the built jar
COPY --from=build /app/target/*.jar app.jar
COPY gitlab.tpl /app/gitlab.tpl
# Expose the application port
EXPOSE 8686

RUN mkdir -p /app/data /app/logs 

# Run the application
ENTRYPOINT ["java", "-jar", "-Dserver.port=8686", "app.jar"]
