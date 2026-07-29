package com.adventurebook.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for the adventure engine.
 *
 * <p>{@code booksDir} is bound as a {@code String} and converted here on purpose. Binding
 * it straight to {@link Path} routes the value through Spring's resource loader, which
 * treats a relative value as a classpath location and quietly strips leading {@code ../}
 * segments — so {@code ../books} resolved to the {@code books} folder on the test
 * classpath instead of the directory next to the module. Converting explicitly keeps the
 * value meaning exactly what it says: a filesystem path relative to the working directory.
 *
 * @param booksDir       directory scanned for book JSON files
 * @param startingHealth health points a player starts with, and the ceiling that
 *                       {@code GAIN_HEALTH} consequences cannot push the player above
 */
@ConfigurationProperties(prefix = "adventure")
public record AdventureProperties(String booksDir, int startingHealth) {

    public AdventureProperties {
        if (booksDir == null || booksDir.isBlank()) {
            throw new IllegalArgumentException("adventure.books-dir must be configured");
        }
        if (startingHealth <= 0) {
            throw new IllegalArgumentException("adventure.starting-health must be greater than zero");
        }
    }

    public Path booksPath() {
        return Path.of(booksDir);
    }
}
