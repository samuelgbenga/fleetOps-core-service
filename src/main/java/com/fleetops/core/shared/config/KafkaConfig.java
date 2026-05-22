package com.fleetops.core.shared.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

import java.util.HashMap;

@Configuration
public class KafkaConfig {

    @Value("${KAFKA_BOOTSTRAP:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> stringProducerFactory() {
        var props = new HashMap<String, Object>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(stringProducerFactory());
    }

    @Value("${kafka.topics.notification-request}")
    private String notificationTopic;

    @Value("${kafka.topics.vehicle-activity}")
    private String vehicleActivityTopic;

    @Value("${kafka.topics.maintenance-events}")
    private String maintenanceTopic;

    @Value("${kafka.topics.breakdown-events}")
    private String breakdownTopic;

    @Value("${kafka.topics.company-events}")
    private String companyTopic;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setRecordMessageConverter(new StringJsonMessageConverter());
        return factory;
    }

    @Bean
    public NewTopic notificationRequestTopic() {
        return new NewTopic(notificationTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic vehicleActivityTopic() {
        return new NewTopic(vehicleActivityTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic maintenanceEventsTopic() {
        return new NewTopic(maintenanceTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic breakdownEventsTopic() {
        return new NewTopic(breakdownTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic companyEventsTopic() {
        return new NewTopic(companyTopic, 1, (short) 1);
    }
}
