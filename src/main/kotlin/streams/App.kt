package streams

import org.apache.kafka.streams.KafkaStreams
import java.io.FileInputStream
import java.util.*


class App {
    private fun loadProperties(): Properties {
        val properties = Properties()
        FileInputStream("src/main/resources/streams.properties").use { fis ->
            properties.load(fis)
            return properties
        }
    }

    fun start() {
        val properties = loadProperties()
        val topology = UserActivityTopology.build(properties)

        val kafkaStreams = KafkaStreams(topology, properties)

        Runtime.getRuntime().addShutdownHook(Thread { kafkaStreams.close() })
        kafkaStreams.start()
    }
}

fun main() {
    val app = App()
    app.start()
}
