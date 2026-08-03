package com.adventurebook.book;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An adventure book: metadata plus the graph of sections the player walks through.
 *
 * <p>Deliberately says nothing about whether the graph is playable. Structural
 * soundness is decided by {@link ValidationEngine}, so a book that breaks the rules can
 * still be loaded, listed and explained to the user rather than silently dropped.
 *
 * @param title      display title
 * @param author     credited author, may be {@code null}
 * @param description short synopsis, may be {@code null}
 * @param tags       presentation labels; never {@code null}
 * @param difficulty advertised difficulty, may be {@code null}
 * @param sections   sections in file order; never {@code null}
 */
public record Book(String title, String author, String description, List<String> tags,
        Difficulty difficulty, List<Section> sections) {

    public Book {
        tags = tags == null ? List.of() : List.copyOf(tags);
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    /** Keeps programmatic fixtures concise when presentation metadata is irrelevant. */
    public Book(String title, String author, Difficulty difficulty, List<Section> sections) {
        this(title, author, null, List.of(), difficulty, sections);
    }

    /**
     * Sections keyed by identifier, keeping the first declaration when ids repeat.
     * Duplicates are a validation concern, not a reason to fail here.
     */
    public Map<String, Section> sectionsById() {
        Map<String, Section> byId = new LinkedHashMap<>();
        for (Section section : sections) {
            if (section.id() != null) {
                byId.putIfAbsent(section.id(), section);
            }
        }
        return byId;
    }

    public Optional<Section> section(String id) {
        String wanted = SectionId.normalise(id);
        if (wanted == null) {
            return Optional.empty();
        }
        return sections.stream().filter(s -> wanted.equals(s.id())).findFirst();
    }

    public List<Section> sectionsOfType(SectionType type) {
        return sections.stream().filter(s -> s.type() == type).toList();
    }
}
