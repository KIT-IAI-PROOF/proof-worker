/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.config.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.web.reactive.function.client.WebClient.builder;

@Configuration
public class ClientSecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(ClientSecurityConfig.class);

    @Bean(name = "defaultWebClient", value = "defaultWebClient")
    public WebClient devDefaultWebclient() {
        logger.info("Created default web client with auth");
        final var builder = builder();
        return builder
                .build();
    }
}
