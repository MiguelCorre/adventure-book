package com.adventurebook.book.testsupport;

import java.util.Arrays;
import java.util.List;

import com.adventurebook.book.Book;
import com.adventurebook.book.Consequence;
import com.adventurebook.book.ConsequenceType;
import com.adventurebook.book.Difficulty;
import com.adventurebook.book.Option;
import com.adventurebook.book.Section;
import com.adventurebook.book.SectionType;

/**
 * Builders for hand-made books, so tests describe the graph they care about and nothing
 * else. Keeps validation and engine tests free of JSON parsing.
 */
public final class Books {

    private Books() {
    }

    public static Book of(Section... sections) {
        return new Book("Test Book", "Test Author", Difficulty.EASY, Arrays.asList(sections));
    }

    public static Book titled(String title, Difficulty difficulty, Section... sections) {
        return new Book(title, "Test Author", difficulty, Arrays.asList(sections));
    }

    public static Section begin(String id, Option... options) {
        return new Section(id, "Section " + id, SectionType.BEGIN, List.of(options));
    }

    public static Section node(String id, Option... options) {
        return new Section(id, "Section " + id, SectionType.NODE, List.of(options));
    }

    public static Section end(String id) {
        return new Section(id, "Section " + id, SectionType.END, List.of());
    }

    public static Option goTo(String description, String gotoId) {
        return new Option(description, gotoId, null);
    }

    public static Option hurts(String description, String gotoId, int damage) {
        return new Option(description, gotoId,
                new Consequence(ConsequenceType.LOSE_HEALTH, damage, "You are hurt."));
    }

    public static Option heals(String description, String gotoId, int amount) {
        return new Option(description, gotoId,
                new Consequence(ConsequenceType.GAIN_HEALTH, amount, "You feel better."));
    }
}
