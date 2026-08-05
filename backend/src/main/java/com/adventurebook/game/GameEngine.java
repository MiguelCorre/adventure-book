package com.adventurebook.game;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.adventurebook.book.Book;
import com.adventurebook.book.Consequence;
import com.adventurebook.book.LoadedBook;
import com.adventurebook.book.Option;
import com.adventurebook.book.Section;
import com.adventurebook.book.SectionType;
import com.adventurebook.config.AdventureProperties;
import com.adventurebook.game.GameExceptions.BookNotPlayableException;
import com.adventurebook.game.GameExceptions.GameFinishedException;
import com.adventurebook.game.GameExceptions.InvalidChoiceException;
import com.adventurebook.game.GameExceptions.SectionNotFoundException;

/**
 * The rules of the game, and the only place they live.
 *
 * <p>Every move is resolved on the server. The client is told what the player can see and
 * nothing more, so a curious reader cannot discover where a choice leads, or what it will
 * cost, by reading the network traffic.
 *
 * <p>Health is clamped to {@code [0, startingHealth]}: a heal can never carry a player
 * above the value they began with, and damage stops at zero, which is the moment the
 * adventure ends.
 */
@Service
public class GameEngine {

    private final int startingHealth;

    @Autowired
    public GameEngine(AdventureProperties properties) {
        this(properties.startingHealth());
    }

    /** Test seam: fix the health bar without a Spring context. */
    GameEngine(int startingHealth) {
        this.startingHealth = startingHealth;
    }

    public int startingHealth() {
        return startingHealth;
    }

    /** Opens a book at its BEGIN section with a full health bar. */
    public GameSession start(LoadedBook loaded) {
        Book book = playableContentOf(loaded);
        Section beginning = book.sectionsOfType(SectionType.BEGIN).getFirst();
        return new GameSession(UUID.randomUUID(), loaded.slug(), beginning.id(), startingHealth,
                statusOf(beginning), null);
    }

    /**
     * Rebuilds a session from saved progress. The saved section must still exist, which
     * guards against a book being edited underneath a save.
     */
    public GameSession resume(LoadedBook loaded, String sectionId, int health) {
        Book book = playableContentOf(loaded);
        Section section = book.section(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId, loaded.slug()));
        int bounded = clamp(health);
        GameStatus status = bounded == 0 ? GameStatus.DEAD : statusOf(section);
        return new GameSession(UUID.randomUUID(), loaded.slug(), section.id(), bounded, status, null);
    }

    /**
     * Applies one choice.
     *
     * <p>Order matters: the consequence is settled before the player moves. A choice that
     * empties the health bar is fatal even when it pointed at an ending, because the
     * player does not survive to read it.
     */
    public GameSession choose(LoadedBook loaded, GameSession session, int optionIndex) {
        if (session.status().isFinished()) {
            throw new GameFinishedException(session.status());
        }

        Book book = playableContentOf(loaded);
        Section current = book.section(session.sectionId())
                .orElseThrow(() -> new SectionNotFoundException(session.sectionId(), loaded.slug()));

        List<Option> options = current.options();
        if (optionIndex < 0 || optionIndex >= options.size()) {
            throw new InvalidChoiceException(optionIndex, options.size());
        }

        Option chosen = options.get(optionIndex);
        Consequence consequence = chosen.consequence();
        int health = clamp(session.health() + healthDeltaOf(consequence));

        if (health == 0) {
            return session.at(current.id(), 0, GameStatus.DEAD, consequence);
        }

        Section next = book.section(chosen.gotoId())
                .orElseThrow(() -> new SectionNotFoundException(chosen.gotoId(), loaded.slug()));
        return session.at(next.id(), health, statusOf(next), consequence);
    }

    private Book playableContentOf(LoadedBook loaded) {
        if (!loaded.isPlayable()) {
            throw new BookNotPlayableException(loaded.slug());
        }
        return loaded.book();
    }

    private static GameStatus statusOf(Section section) {
        return section.isEnding() ? GameStatus.WON : GameStatus.IN_PROGRESS;
    }

    private static long healthDeltaOf(Consequence consequence) {
        if (consequence == null) {
            return 0;
        }
        return switch (consequence.type()) {
            case LOSE_HEALTH -> -consequence.value();
            case GAIN_HEALTH -> consequence.value();
        };
    }

    private int clamp(long health) {
        return Math.clamp(health, 0, startingHealth);
    }
}
