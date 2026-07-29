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
public record BookSummary(String slug, String title, String author, Difficulty difficulty, int sectionCount,
        boolean valid, List<ValidationIssueView> issues, boolean hasSave) {

    public static BookSummary from(LoadedBook loaded, boolean hasSave) {
        return new BookSummary(
                loaded.slug(),
                loaded.title(),
                loaded.author(),
                loaded.difficulty(),
                loaded.sectionCount(),
                loaded.isPlayable(),
                loaded.report().issues().stream().map(ValidationIssueView::from).toList(),
                hasSave);
    }
}
