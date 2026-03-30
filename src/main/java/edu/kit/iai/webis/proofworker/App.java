/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main start class
 */
@SpringBootApplication
@ComponentScan(basePackages = {"edu.kit.iai.webis.proofworker", "edu.kit.iai.webis.proofutils"})
public class App {

    /**
     * Main entry point
     *
     * @param args Program args
     */
    public static void main(final String... args) {
    	SpringApplication.run(App.class, args);
    }

}
