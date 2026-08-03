package com.adventurebook.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.adventurebook.api.dto.BookSummary;

class LoadedBookTest {

    @Test
    void countsWordsAcrossEverySectionAndRoundsReadingTimeUp() {
        Book book = new Book("Long Read", null, Difficulty.EASY, List.of(
                new Section("1", "word ".repeat(199).trim(), SectionType.BEGIN,
                        List.of(new Option("Finish", "2", null))),
                new Section("2", "last two", SectionType.END, List.of())));
        LoadedBook loaded = new LoadedBook("long-read", book, ValidationReport.valid());

        assertThat(loaded.wordCount()).isEqualTo(201);
        assertThat(BookSummary.from(loaded, false).readingMinutes()).isEqualTo(2);
    }

    @Test
    void givesUnreadableFilesNoReadingTime() {
        LoadedBook unreadable = LoadedBook.unreadable("empty", "file is empty");

        assertThat(unreadable.wordCount()).isZero();
        assertThat(BookSummary.from(unreadable, false).readingMinutes()).isZero();
    }

    @Test
    void givesEveryReadableBookAtLeastOneMinute() {
        Book book = new Book("Tiny", null, Difficulty.EASY,
                List.of(new Section("1", "Done.", SectionType.END, List.of())));
        LoadedBook loaded = new LoadedBook("tiny", book, ValidationReport.valid());

        assertThat(BookSummary.from(loaded, false).readingMinutes()).isOne();
    }

    @Test
    void givesAParsedBookWithNoSectionsTheMinimumRatherThanTheUnreadableSentinel() {
        Book book = new Book("Empty draft", null, Difficulty.EASY, List.of());
        LoadedBook loaded = new LoadedBook("empty-draft", book, new ValidationEngine().validate(book));

        assertThat(loaded.wordCount()).isZero();
        assertThat(BookSummary.from(loaded, false).readingMinutes()).isOne();
    }
}
