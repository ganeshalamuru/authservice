FROM azul/zulu-openjdk:21 AS BUILD_IMAGE
ENV APP_HOME=/root/dev/authservice
RUN mkdir -p $APP_HOME/src/main/java
WORKDIR $APP_HOME
COPY ./build.gradle ./gradlew ./gradlew.bat $APP_HOME
COPY gradle $APP_HOME/gradle
COPY ./src $APP_HOME/src/
RUN chmod +x gradlew
RUN ./gradlew clean build -x test

FROM azul/zulu-openjdk-alpine:21-jre-headless
WORKDIR /root/
COPY --from=BUILD_IMAGE '/root/dev/authservice/build/libs/authservice-0.0.1-SNAPSHOT.jar' '/app/authservice.jar'
EXPOSE 8081
CMD ["java","-jar","/app/authservice.jar"]
