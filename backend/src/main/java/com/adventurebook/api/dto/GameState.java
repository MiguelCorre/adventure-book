package com.adventurebook.api.dto;

import java.util.List;
import java.util.UUID;

import com.adventurebook.book.Consequence;
import com.adventurebook.book.LoadedBook;
import com.adventurebook.book.Section;
import com.adventurebook.book.SectionType;
import com.adventurebook.game.GameSession;
import com.adventurebook.game.GameStatus;

/**
 * Everything the player is allowed to know right now.
 *
 * <p>The shape of this record is the anti-spoiler contract. Options carry an index and
 * the words on the button, and nothing else: no destination, no consequence. A reader
 * with the network tab open learns no more than a reader holding the paperback.
 */
public record GameState(UUID gameId, String bookSlug, String bookTitle, int health, int maxHealth,
        GameStatus status, SectionView section, ConsequenceView lastConsequence) {

    /** @param options choices in presentation order; the index is what the client sends back */
    public record SectionView(String id, String text, SectionType type, List<OptionView> options) {
    }

    public record OptionView(int index, String description) {
    }

    public record ConsequenceView(String type, int value, String text) {
    }

    public static GameState of(GameSession session, LoadedBook book, Section section, int maxHealth) {
        List<OptionView> options = java.util.stream.IntStream.range(0, section.options().size())
                .mapToObj(index -> new OptionView(index, section.options().get(index).description()))
                .toList();

        return new GameState(
                session.id(),
                book.slug(),
                book.title(),
                session.health(),
                maxHealth,
                session.status(),
                new SectionView(section.id(), section.text(), section.type(), options),
                consequenceView(session.lastConsequence()));
    }

    private static ConsequenceView consequenceView(Consequence consequence) {
        return consequence == null ? null
                : new ConsequenceView(consequence.type().name(), consequence.value(), consequence.text());
    }
}
