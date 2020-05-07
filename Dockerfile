#
# build
#

FROM adoptopenjdk:14-jdk-hotspot AS build

# download and cache the version of gradle in use
COPY ./gradle /opt/hedera-proof-of-action/gradle
COPY ./gradlew /opt/hedera-proof-of-action/gradlew

RUN cd /opt/hedera-proof-of-action && ./gradlew --no-daemon

# copy in just enough to cache dependencies
COPY ./build.gradle /opt/hedera-proof-of-action/build.gradle
COPY ./settings.gradle /opt/hedera-proof-of-action/settings.gradle

RUN cd /opt/hedera-proof-of-action && ./gradlew --no-daemon compileJava

# now, finally copy in the source and build a JAR
COPY ./src /opt/hedera-proof-of-action/src

RUN cd /opt/hedera-proof-of-action && ./gradlew --no-daemon build

#
# run
#

FROM adoptopenjdk:14-jre-hotspot

# make a place to put our built JAR and copy it to there
WORKDIR /srv
COPY --from=build /opt/hedera-proof-of-action/build/libs/hedera-proof-of-action.jar /srv/hedera-proof-of-action.jar

# run the micro service
CMD java "-jar" "hedera-proof-of-action.jar"
EXPOSE 8080
