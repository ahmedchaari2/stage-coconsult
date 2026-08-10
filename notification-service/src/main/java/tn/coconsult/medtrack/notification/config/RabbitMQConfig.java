package tn.coconsult.medtrack.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declare le meme exchange qu'appointment-service (idempotent, les deux services le declarent
 * chacun de leur cote) plus la queue et les bindings que ce service consomme. Queue durable :
 * les evenements publies pendant un arret de ce service restent en file et sont traites au
 * redemarrage (voir test de resilience).
 */
@Configuration
public class RabbitMQConfig {

    @Value("${medtrack.rabbitmq.appointment-events-exchange}")
    private String exchangeName;

    @Value("${medtrack.rabbitmq.appointment-events-queue}")
    private String queueName;

    @Value("${medtrack.rabbitmq.appointment-created-routing-key}")
    private String createdRoutingKey;

    @Value("${medtrack.rabbitmq.appointment-status-changed-routing-key}")
    private String statusChangedRoutingKey;

    @Bean
    public TopicExchange appointmentEventsExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue appointmentEventsQueue() {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding appointmentCreatedBinding(Queue appointmentEventsQueue, TopicExchange appointmentEventsExchange) {
        return BindingBuilder.bind(appointmentEventsQueue).to(appointmentEventsExchange).with(createdRoutingKey);
    }

    @Bean
    public Binding appointmentStatusChangedBinding(Queue appointmentEventsQueue, TopicExchange appointmentEventsExchange) {
        return BindingBuilder.bind(appointmentEventsQueue).to(appointmentEventsExchange).with(statusChangedRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTrustedPackages("tn.coconsult.medtrack.events");
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
