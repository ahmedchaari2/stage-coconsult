package tn.coconsult.medtrack.appointment.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Producteur : ne declare que l'exchange (meme nom que cote notification-service, declaration
 * idempotente des deux cotes). La queue/les bindings sont l'affaire du consommateur.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${medtrack.rabbitmq.appointment-events-exchange}")
    private String exchangeName;

    @Bean
    public TopicExchange appointmentEventsExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    // trustedPackages : cote emission ca n'affecte que RabbitTemplate.receive (non utilise ici),
    @Bean
    public MessageConverter jsonMessageConverter() {
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTrustedPackages("tn.coconsult.medtrack.events");
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
