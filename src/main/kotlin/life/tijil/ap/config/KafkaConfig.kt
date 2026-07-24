package life.tijil.ap.config

import life.tijil.ap.invoice.InvoiceReceived
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
class KafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers}") private val bootstrap: String,
    @Value("\${spring.kafka.consumer.group-id}") private val groupId: String,
) {
    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        val props = mapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            JsonSerializer.ADD_TYPE_INFO_HEADERS to false,
        )
        return KafkaTemplate(DefaultKafkaProducerFactory(props))
    }

    @Bean
    fun invoiceListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, InvoiceReceived> {
        val deserializer = JsonDeserializer(InvoiceReceived::class.java).apply {
            addTrustedPackages("life.tijil.ap.*")
            setUseTypeHeaders(false)
        }
        val props = mapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        )
        val factory = ConcurrentKafkaListenerContainerFactory<String, InvoiceReceived>()
        factory.consumerFactory = DefaultKafkaConsumerFactory(props, StringDeserializer(), deserializer)
        return factory
    }
}
