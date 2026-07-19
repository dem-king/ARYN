FROM eclipse-temurin:17-jdk-jammy

MAINTAINER xuxueli

ENV PARAMS=""

ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

ARG XXL_JOB_VERSION=3.2.0
COPY target/xxl-job-admin-${XXL_JOB_VERSION}.jar /app.jar

ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /app.jar $PARAMS"]
