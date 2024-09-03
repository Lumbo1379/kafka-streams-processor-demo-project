plugins {
    kotlin("jvm") version "2.0.0"
    application
    id("com.gradleup.shadow") version "8.3.0"
    id("com.google.protobuf") version "0.9.4"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()

    maven {
        url = uri("https://packages.confluent.io/maven")
    }
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:3.25.4")

    implementation("org.apache.kafka:kafka-streams:3.8.0")
    implementation("org.apache.logging.log4j:log4j-api:2.23.1")
    implementation("org.apache.logging.log4j:log4j-core:2.23.1")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.23.1")

    implementation("io.confluent:kafka-schema-registry-client:7.7.0")
    implementation("io.confluent:kafka-streams-protobuf-serde:7.7.0")

    testImplementation(kotlin("test"))
    testImplementation("org.apache.kafka:kafka-streams-test-utils:3.8.0")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
}

application {
    mainClass.set("streams.AppKt")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

protobuf {
    protoc {
        // kafka-streams requires protobuf < 4.x.x
        artifact = "com.google.protobuf:protoc:3.25.4"
    }
}