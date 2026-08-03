package com.adventurebook.book;

import java.util.List;
import java.util.Optional;

/**
 * A book file as the application sees it: what was on disk, and what is wrong with it.
 *
 * <p>Loading never fails. A file that could not be parsed still produces a
 * {@code LoadedBook} carrying an {@link ValidationRule#UNREADABLE} report, so the library
 * can list it and explain the problem instead of pretending it does not exist.
 *
 * @param slug   filename without the {@code .json} extension; the public identifier
 * @param book   parsed content, {@code null} when the file could not be read
 * @param report every reason this book cannot be played; empty when it can
 */
public record LoadedBook(String slug, Book book, ValidationReport report) {

    public static LoadedBook unreadable(String slug, String reason) {
        return new LoadedBook(slug, null, ValidationReport.unreadable(reason));
    }

    public boolean isPlayable() {
        return book != null && report.isValid();
    }

    public Optional<Book> content() {
        return Optional.ofNullable(book);
    }

    /** Falls back to the slug so an unreadable file still has something to show. */
    public String title() {
        if (book == null || book.title() == null || book.title().isBlank()) {
            return slug;
        }
        return book.title();
    }

    public String author() {
        return book == null ? null : book.author();
    }

    public String description() {
        return book == null ? null : book.description();
    }

    public List<String> tags() {
        return book == null ? List.of() : book.tags();
    }

    public Difficulty difficulty() {
        return book == null ? null : book.difficulty();
    }

    public int sectionCount() {
        return book == null ? 0 : book.sections().size();
    }

    /** Counts the prose the player reads; option labels and metadata are excluded. */
    public int wordCount() {
        if (book == null) {
            return 0;
        }
        return book.sections().stream()
                .mapToInt(section -> section.text().trim().split("\\s+").length)
                .sum();
    }
}
