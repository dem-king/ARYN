FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY target/*.jar app.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=cloud

EXPOSE 7001

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]
