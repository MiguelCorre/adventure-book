package com.adventurebook.api.dto;

import java.util.List;

import com.adventurebook.book.Difficulty;
import com.adventurebook.book.LoadedBook;

/**
 * A book as the library lists it.
 *
 * <p>Carries no sections. The catalogue never needs the story, and withholding it keeps
 * the shape of the book out of the browser until the player actually starts reading.
 *
 * @param valid   whether the book can be played; drives the disabled state of the button
 * @param issues  why it cannot, shown under the card
 * @param hasSave whether saved progress exists for this book
 */
public record BookSummary(String slug, String title, String author, String description, List<String> tags,
        Difficulty difficulty, int sectionCount, int readingMinutes, boolean valid,
        List<ValidationIssueView> issues, boolean hasSave) {

    /** Derived at a conventional reading rate so authors never maintain duplicate metadata. */
    private static final int WORDS_PER_MINUTE = 200;

    public static BookSummary from(LoadedBook loaded, boolean hasSave) {
        return new BookSummary(
                loaded.slug(),
                loaded.title(),
                loaded.author(),
                loaded.description(),
                loaded.tags(),
                loaded.difficulty(),
                loaded.sectionCount(),
                readingMinutes(loaded),
                loaded.isPlayable(),
                loaded.report().issues().stream().map(ValidationIssueView::from).toList(),
                hasSave);
    }

    private static int readingMinutes(LoadedBook loaded) {
        if (loaded.content().isEmpty()) {
            return 0;
        }
        int words = loaded.wordCount();
        return Math.max(1, (words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE);
    }
}
