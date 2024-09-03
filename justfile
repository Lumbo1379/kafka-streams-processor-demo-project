@_default:
    just --list


# Start the Kafka Streams processor
up:
    docker compose up


# Start Kafka
up-kafka:
    docker compose -f docker-compose-kafka.yml up


# Destroy everything
destroy:
    docker compose -f docker-compose.yml -f docker-compose-kafka.yml down -v


# Start the data generators
start-datagen:
    curl -X POST -H "Content-Type: application/json" -d @./dev/pages-click-connector.json http://localhost:8083/connectors/
    curl -X POST -H "Content-Type: application/json" -d @./dev/pages-view-connector.json http://localhost:8083/connectors/
    curl -X POST -H "Content-Type: application/json" -d @./dev/pages-scroll-connector.json http://localhost:8083/connectors/


# Pause the data generators
pause-datagen:
    curl -X PUT http://localhost:8083/connectors/datagen-pages-click/pause
    curl -X PUT http://localhost:8083/connectors/datagen-pages-view/pause
    curl -X PUT http://localhost:8083/connectors/datagen-pages-scroll/pause


# Resume the data generators
resume-datagen:
    curl -X PUT http://localhost:8083/connectors/datagen-pages-click/resume
    curl -X PUT http://localhost:8083/connectors/datagen-pages-view/resume
    curl -X PUT http://localhost:8083/connectors/datagen-pages-scroll/resume


build:
    ./gradlew shadowJar
    docker compose build