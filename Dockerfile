FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY . .
RUN mvn install
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/start/jar/ ./jar/
COPY --from=builder /build/config/ ./config/
COPY --from=builder /build/tables/json/ ./tables/json/
COPY --from=builder /build/admin-ui/ ./admin-ui/