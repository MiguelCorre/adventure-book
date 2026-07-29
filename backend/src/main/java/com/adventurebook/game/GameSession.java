package com.adventurebook.game;

import java.util.UUID;

import com.adventurebook.book.Consequence;

/**
 * One play-through, at one instant.
 *
 * <p>Immutable on purpose: every choice produces a new session rather than mutating the
 * old one, so the engine has no hidden state, tests can compare before and after
 * directly, and concurrent reads of the registry can never observe a half-applied move.
 *
 * @param id              server-issued handle the client plays through
 * @param bookSlug        book being played
 * @param sectionId       section the player is currently reading
 * @param health          current health, never below zero nor above the configured ceiling
 * @param status          whether the adventure is still running, won, or lost
 * @param lastConsequence effect applied by the most recent choice, {@code null} if none
 */
public record GameSession(UUID id, String bookSlug, String sectionId, int health, GameStatus status,
        Consequence lastConsequence) {

    GameSession at(String nextSectionId, int newHealth, GameStatus newStatus, Consequence consequence) {
        return new GameSession(id, bookSlug, nextSectionId, newHealth, newStatus, consequence);
    }
}
