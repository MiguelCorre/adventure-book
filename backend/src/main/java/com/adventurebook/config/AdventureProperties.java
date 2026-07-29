package com.adventurebook.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for the adventure engine.
 *
 * @param booksDir       directory scanned for book JSON files
 * @param startingHealth health points a player starts with, and the ceiling that
 *                       {@code GAIN_HEALTH} consequences cannot push the player above
 */
@ConfigurationProperties(prefix = "adventure")
public record AdventureProperties(Path booksDir, int startingHealth) {

    public AdventureProperties {
        if (booksDir == null) {
            throw new IllegalArgumentException("adventure.books-dir must be configured");
        }
        if (startingHealth <= 0) {
            throw new IllegalArgumentException("adventure.starting-health must be greater than zero");
        }
    }
}
