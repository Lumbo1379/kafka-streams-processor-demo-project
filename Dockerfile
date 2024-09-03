FROM amazoncorretto:21

WORKDIR /app

COPY build/libs/kafka-streams-demo-project-1.0-SNAPSHOT-all.jar /app/
COPY src/main/resources/ /app/src/main/resources/

CMD ["java", "-jar", "/app/kafka-streams-demo-project-1.0-SNAPSHOT-all.jar"]