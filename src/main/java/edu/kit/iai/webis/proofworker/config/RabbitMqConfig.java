/*
 * Copyright (c) 2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics
 */

package edu.kit.iai.webis.proofworker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for the worker service.
 * Ensures proper JSON serialization of messages.
 */
@Configuration
public class RabbitMqConfig {

    /**
     * Configure RabbitTemplate with JSON message converter.
     * This is essential for properly serializing BlockStatusUpdate objects.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter());
        return rabbitTemplate;
    }

    /**
     * JSON message converter for RabbitMQ messages.
     * Configured with JavaTimeModule to handle Java 8 date/time types like Instant.
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
