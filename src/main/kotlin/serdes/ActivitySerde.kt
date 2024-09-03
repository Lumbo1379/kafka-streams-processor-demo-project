package serdes

import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer
import processors.ActivityProcessor
import store.activity.StoreActivity.ActivityStore

class ActivitySerializer : Serializer<ActivityProcessor> {
    override fun serialize(topic: String, data: ActivityProcessor): ByteArray {
        val activityStore = ActivityStore.newBuilder()
            .addAllPageIds(data.viewedPages)
            .build()

        return activityStore.toByteArray()
    }

    override fun close() {}
}

class ActivityDeserializer : Deserializer<ActivityProcessor> {
    override fun deserialize(topic: String, data: ByteArray?): ActivityProcessor? {
        return data?.let { ActivityProcessor.from(ActivityStore.parseFrom(data)) }
    }

    override fun close() {}
}

class ActivitySerde: Serdes.WrapperSerde<ActivityProcessor>(ActivitySerializer(), ActivityDeserializer())