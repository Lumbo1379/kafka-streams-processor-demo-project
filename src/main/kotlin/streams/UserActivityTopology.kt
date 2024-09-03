package streams

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.streams.serdes.protobuf.KafkaProtobufSerde
import models.ActivityEnvelope
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.state.Stores
import pages.click.PagesClick.ClickPagesEvent
import pages.scroll.PagesScroll.ScrollPagesEvent
import pages.view.PagesView.ViewPagesEvent
import processors.ActivityProcessor
import serdes.ActivitySerde
import users.activity.UsersActivity.ActivityUsersEvent
import java.time.Duration
import java.util.Properties

class UserActivityTopology {

    companion object {
        fun build(properties: Properties): Topology {
            val serdeConfig = hashMapOf(
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to properties.getProperty(
                    AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
                )
            )

            val clickPagesEventSerde = KafkaProtobufSerde(ClickPagesEvent::class.java)
            clickPagesEventSerde.configure(serdeConfig, false)

            val scrollPagesEventSerde = KafkaProtobufSerde(ScrollPagesEvent::class.java)
            scrollPagesEventSerde.configure(serdeConfig, false)

            val viewPagesEventSerde = KafkaProtobufSerde(ViewPagesEvent::class.java)
            viewPagesEventSerde.configure(serdeConfig, false)

            val activityUsersEventSerde = KafkaProtobufSerde(ActivityUsersEvent::class.java)
            activityUsersEventSerde.configure(serdeConfig, false)

            val inactivityGap = Duration.ofSeconds(properties["inactivity.gap"] as Long)

            val aggregatedActivityStore =
                Stores.persistentSessionStore("aggregated-activity-store", Duration.ofDays(1))

            val builder = StreamsBuilder()

            val clickPagesStream = builder.stream(
                properties.getProperty("pages.click.topic"),
                Consumed.with(Serdes.String(), clickPagesEventSerde)
            )
                .mapValues { _, value -> ActivityEnvelope(clickPagesEvent = value) }

            val scrollPagesStream = builder.stream(
                properties.getProperty("pages.scroll.topic"),
                Consumed.with(Serdes.String(), scrollPagesEventSerde)
            )
                .mapValues { _, value -> ActivityEnvelope(scrollPagesEvent = value) }

            val viewPagesStream = builder.stream(
                properties.getProperty("pages.view.topic"),
                Consumed.with(Serdes.String(), viewPagesEventSerde)
            )
                .mapValues { _, value -> ActivityEnvelope(viewPagesEvent = value) }

            clickPagesStream
                .merge(scrollPagesStream)
                .merge(viewPagesStream)
                .groupByKey()
                .windowedBy(SessionWindows.ofInactivityGapWithNoGrace(inactivityGap))
                .aggregate(
                    { ActivityProcessor() },
                    { _, value, activityProcessor -> activityProcessor.process(value) },
                    { _, leftValue, rightValue -> ActivityProcessor.combine(leftValue, rightValue) },
                    Materialized.`as`<String, ActivityProcessor>(aggregatedActivityStore)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(ActivitySerde())
                )
                .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded().shutDownWhenFull()))
                .toStream()
                .filter { _, value -> value.viewedPages.isNotEmpty() }
                .peek { windowKey, value -> println("User ${windowKey.key()}'s session ended, they viewed page ids ${value.viewedPages}") }
                .map { windowKey, value -> KeyValue.pair(
                    windowKey.key(),
                    ActivityUsersEvent.newBuilder()
                        .addAllPageIds(value.viewedPages)
                        .build()
                ) }
                .to(
                    properties.getProperty("users.activity.topic"),
                    Produced.with(Serdes.String(), activityUsersEventSerde)
                )

            return builder.build()
        }
    }
}