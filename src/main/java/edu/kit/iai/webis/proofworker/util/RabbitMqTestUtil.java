/*
 * Copyright (c) 2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics
 */

package edu.kit.iai.webis.proofworker.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import edu.kit.iai.webis.proofworker.model.BlockStatusUpdate;

import java.time.Instant;

/**
 * Manual test utility to verify RabbitMQ message sending.
 * This can be run standalone to test if messages reach RabbitMQ.
 * 
 * Usage:
 * 1. Update RabbitMQ connection details (host, port, username, password)
 * 2. Run this class as a Java application
 * 3. Check RabbitMQ management UI or monitoring service for received messages
 */
public class RabbitMqTestUtil {

    public static void main(String[] args) {
        // Configure RabbitMQ connection
        String host = "localhost";
        int port = 5672;
        String username = "admin";
        String password = "admin";
        String exchange = "execution-status-exchange";
        String routingKey = "execution.status.update";

        System.out.println("=== RabbitMQ Test Utility ===");
        System.out.println("Connecting to RabbitMQ at " + host + ":" + port);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            System.out.println("✓ Connected to RabbitMQ");

            // Create test BlockStatusUpdate
            BlockStatusUpdate update = BlockStatusUpdate.builder()
                    .executionId("test-execution-" + System.currentTimeMillis())
                    .blockId("test-block-123")
                    .status("INITIALIZED")
                    .message("Test message from RabbitMqTestUtil")
                    .timestamp(Instant.now())
                    .build();

            // Serialize to JSON using Jackson
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            String json = objectMapper.writeValueAsString(update);

            System.out.println("Sending message to exchange: " + exchange);
            System.out.println("Routing key: " + routingKey);
            System.out.println("Message: " + json);

            // Publish message
            channel.basicPublish(
                    exchange,
                    routingKey,
                    null,
                    json.getBytes("UTF-8")
            );

            System.out.println("✓ Message sent successfully!");
            System.out.println("\nCheck the monitoring service or RabbitMQ management UI to verify message arrival.");
            System.out.println("Queue should be: execution-status-queue");

        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
            System.err.println("\nPossible issues:");
            System.err.println("1. RabbitMQ is not running");
            System.err.println("2. Wrong connection details (host, port, credentials)");
            System.err.println("3. Exchange 'execution-status-exchange' doesn't exist");
            System.err.println("4. Network connectivity issues");
        }
    }
}
