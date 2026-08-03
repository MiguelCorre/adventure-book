package com.adventurebook.book;

import java.util.List;

/**
 * One numbered passage of a book, with the choices it offers.
 *
 * @param id      canonical section identifier
 * @param title   optional heading shown above the passage
 * @param text    prose shown to the player; never blank
 * @param type    role of this section in the story graph; never {@code null}
 * @param options choices offered; never {@code null}, empty for ending sections
 */
public record Section(String id, String title, String text, SectionType type, List<Option> options) {

    public Section {
        id = SectionId.normalise(id);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(requiredField(id, "text"));
        }
        if (type == null) {
            throw new IllegalArgumentException(requiredField(id, "type"));
        }
        options = options == null ? List.of() : List.copyOf(options);
    }

    /** Keeps programmatic fixtures concise when a heading is not relevant. */
    public Section(String id, String text, SectionType type, List<Option> options) {
        this(id, null, text, type, options);
    }

    public boolean isEnding() {
        return type == SectionType.END;
    }

    public boolean isBeginning() {
        return type == SectionType.BEGIN;
    }

    public boolean hasOptions() {
        return !options.isEmpty();
    }

    private static String requiredField(String id, String field) {
        return id == null ? "section %s is required".formatted(field)
                : "section '%s' %s is required".formatted(id, field);
    }
}
