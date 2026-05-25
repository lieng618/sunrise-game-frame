FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY start/jar/ ./jar/
COPY config/ ./config/
COPY tables/json/ ./tables/json/