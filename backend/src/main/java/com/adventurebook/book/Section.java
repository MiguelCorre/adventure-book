package com.adventurebook.book;

import java.util.List;

/**
 * One numbered passage of a book, with the choices it offers.
 *
 * @param id      canonical section identifier
 * @param text    prose shown to the player
 * @param type    role of this section in the story graph
 * @param options choices offered; never {@code null}, empty for ending sections
 */
public record Section(String id, String text, SectionType type, List<Option> options) {

    public Section {
        id = SectionId.normalise(id);
        options = options == null ? List.of() : List.copyOf(options);
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
}
