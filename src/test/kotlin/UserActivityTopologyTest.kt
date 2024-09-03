import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.streams.serdes.protobuf.KafkaProtobufSerde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pages.click.PagesClick.ClickPagesEvent
import pages.scroll.PagesScroll.ScrollPagesEvent
import pages.view.PagesView.ViewPagesEvent
import streams.UserActivityTopology
import users.activity.UsersActivity.ActivityUsersEvent
import java.util.*


class UserActivityTopologyTest {
    private var testDriver: TopologyTestDriver? = null
    private var testClickPagesTopic: TestInputTopic<String, ClickPagesEvent>? = null
    private var testScrollPagesTopic: TestInputTopic<String, ScrollPagesEvent>? = null
    private var testViewPagesTopic: TestInputTopic<String, ViewPagesEvent>? = null
    private var testActivityUsersTopic: TestOutputTopic<String, ActivityUsersEvent>? = null

    private val inactivityGapMs = 10_000L

    @BeforeEach
    fun setup() {
        val properties = Properties()
        properties[StreamsConfig.APPLICATION_ID_CONFIG] = "test"
        properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "dummy:1234"
        properties[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = "mock://schema-registry"
        properties["pages.click.topic"] = "pub.pages.click.v1"
        properties["pages.scroll.topic"] = "pub.pages.scroll.v1"
        properties["pages.view.topic"] = "pub.pages.view.v1"
        properties["users.activity.topic"] = "pub.users.activity.v1"
        properties["inactivity.gap"] = inactivityGapMs / 1000

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

        val topology = UserActivityTopology.build(properties)

        testDriver = TopologyTestDriver(topology, properties)

        testClickPagesTopic =
            testDriver!!.createInputTopic(
                properties.getProperty("pages.click.topic"),
                Serdes.String().serializer(),
                clickPagesEventSerde.serializer()
            )

        testScrollPagesTopic =
            testDriver!!.createInputTopic(
                properties.getProperty("pages.scroll.topic"),
                Serdes.String().serializer(),
                scrollPagesEventSerde.serializer()
            )

        testViewPagesTopic =
            testDriver!!.createInputTopic(
                properties.getProperty("pages.view.topic"),
                Serdes.String().serializer(),
                viewPagesEventSerde.serializer()
            )

        testActivityUsersTopic =
            testDriver!!.createOutputTopic(
                properties.getProperty("users.activity.topic"),
                Serdes.String().deserializer(),
                activityUsersEventSerde.deserializer()
            )

    }

    @AfterEach
    fun teardown() {
        testDriver!!.close()
    }

    @Test
    fun testStoresUniquePageIds() {
        val userId = 1

        val viewPagesEvent1 = ViewPagesEvent.newBuilder()
            .setUserId(userId)
            .setPageId("shoes")
            .build()

        val viewPagesEvent2 = ViewPagesEvent.newBuilder()
            .setUserId(userId)
            .setPageId("shoes")
            .build()

        val viewPagesEvent3 = ViewPagesEvent.newBuilder()
            .setUserId(userId)
            .setPageId("hats")
            .build()

        testViewPagesTopic!!.pipeInput(userId.toString(), viewPagesEvent1, 0)
        testViewPagesTopic!!.pipeInput(userId.toString(), viewPagesEvent2, 1_000)
        testViewPagesTopic!!.pipeInput(userId.toString(), viewPagesEvent3, 5_000)

        assertThat(testActivityUsersTopic!!.isEmpty)

        val eventToMoveEventTime = ViewPagesEvent.newBuilder()
            .setUserId(2)
            .setPageId("coats")
            .build()

        testViewPagesTopic!!.pipeInput("2", eventToMoveEventTime, inactivityGapMs * 2)

        assertThat(!testActivityUsersTopic!!.isEmpty)

        val activityUsersRecords = testActivityUsersTopic!!.readRecordsToList()
        assertThat(activityUsersRecords).hasSize(1)

        val activity = activityUsersRecords[0]
        assertThat(activity.key).isEqualTo(userId.toString())
        assertThat(activity.value.pageIdsList).isEqualTo(listOf("shoes", "hats"))
    }

    @Test
    fun testClickAndScrollEventsExtendSessionWindow() {
        val userId = 1

        val viewPagesEvent = ViewPagesEvent.newBuilder()
            .setUserId(userId)
            .setPageId("shoes")
            .build()

        val scrollPagesEvent = ScrollPagesEvent.newBuilder()
            .setUserId(userId)
            .build()

        val clickPagesEvent = ClickPagesEvent.newBuilder()
            .setUserId(userId)
            .build()

        testViewPagesTopic!!.pipeInput(userId.toString(), viewPagesEvent, 0)
        testScrollPagesTopic!!.pipeInput(userId.toString(), scrollPagesEvent, 9_000)
        testClickPagesTopic!!.pipeInput(userId.toString(), clickPagesEvent, 18_000)

        assertThat(testActivityUsersTopic!!.isEmpty)

        val eventToMoveEventTime = ViewPagesEvent.newBuilder()
            .setUserId(2)
            .setPageId("coats")
            .build()

        testViewPagesTopic!!.pipeInput("2", eventToMoveEventTime, inactivityGapMs * 3)

        assertThat(!testActivityUsersTopic!!.isEmpty)

        val activityUsersRecords = testActivityUsersTopic!!.readRecordsToList()
        assertThat(activityUsersRecords).hasSize(1)

        val activity = activityUsersRecords[0]
        assertThat(activity.key).isEqualTo(userId.toString())
        assertThat(activity.value.pageIdsList).isEqualTo(listOf("shoes"))
    }

    @Test
    fun testFiltersOutNoPagesViewed() {
        val userId = 1

        val scrollPagesEvent = ScrollPagesEvent.newBuilder()
            .setUserId(userId)
            .build()

        val clickPagesEvent = ClickPagesEvent.newBuilder()
            .setUserId(userId)
            .build()

        testScrollPagesTopic!!.pipeInput(userId.toString(), scrollPagesEvent, 0)
        testClickPagesTopic!!.pipeInput(userId.toString(), clickPagesEvent, 5_000)

        assertThat(testActivityUsersTopic!!.isEmpty)

        val eventToMoveEventTime = ViewPagesEvent.newBuilder()
            .setUserId(2)
            .setPageId("coats")
            .build()

        testViewPagesTopic!!.pipeInput("2", eventToMoveEventTime, inactivityGapMs * 2)

        assertThat(testActivityUsersTopic!!.isEmpty)
    }

    @Test
    fun testGroupsActivityByUserId() {
        val userId1 = 1
        val userId2 = 2

        val viewPagesEvent1 = ViewPagesEvent.newBuilder()
            .setUserId(userId1)
            .setPageId("shoes")
            .build()

        val viewPagesEvent2 = ViewPagesEvent.newBuilder()
            .setUserId(userId2)
            .setPageId("hats")
            .build()

        testViewPagesTopic!!.pipeInput(userId1.toString(), viewPagesEvent1, 0)
        testViewPagesTopic!!.pipeInput(userId2.toString(), viewPagesEvent2, 5_000)

        assertThat(testActivityUsersTopic!!.isEmpty)

        val eventToMoveEventTime = ViewPagesEvent.newBuilder()
            .setUserId(3)
            .setPageId("coats")
            .build()

        testViewPagesTopic!!.pipeInput("3", eventToMoveEventTime, inactivityGapMs * 2)

        assertThat(!testActivityUsersTopic!!.isEmpty)

        val activityUsersRecords = testActivityUsersTopic!!.readRecordsToList()
        assertThat(activityUsersRecords).hasSize(2)

        val activity1 = activityUsersRecords[0]
        assertThat(activity1.key).isEqualTo(userId1.toString())
        assertThat(activity1.value.pageIdsList).isEqualTo(listOf("shoes"))

        val activity2 = activityUsersRecords[1]
        assertThat(activity2.key).isEqualTo(userId2.toString())
        assertThat(activity2.value.pageIdsList).isEqualTo(listOf("hats"))
    }
}