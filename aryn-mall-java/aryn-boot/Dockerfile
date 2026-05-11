FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY target/*.jar app.jar

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+ExitOnOutOfMemoryError"

EXPOSE 9999

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
