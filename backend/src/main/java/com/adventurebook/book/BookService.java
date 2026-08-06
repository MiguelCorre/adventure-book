package com.adventurebook.book;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

/**
 * Library queries over the loaded books.
 *
 * <p>Searching and filtering happen here rather than in the browser so the contract is
 * the same for every client and the frontend stays a rendering layer.
 *
 * <p>Invalid books are never hidden. The brief asks the home page to list <em>all</em>
 * books, and a player is better served by seeing a broken book with an explanation than
 * by wondering where it went. They are, however, sorted after the playable ones so the
 * reader sees what they can actually start at the top of the page.
 */
@Service
public class BookService {

    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = repository;
    }

    public List<LoadedBook> search(String query, Set<Difficulty> difficulties, Set<String> tags) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return repository.findAll().stream()
                .filter(book -> matchesText(book, needle))
                .filter(book -> matchesDifficulty(book, difficulties))
                .filter(book -> matchesTags(book, tags))
                // Playable books first. The sort is stable, so within each group the
                // repository's alphabetical order is preserved.
                .sorted(Comparator.comparing(LoadedBook::isPlayable).reversed())
                .toList();
    }

    public List<String> tags() {
        TreeSet<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        repository.findAll().stream()
                .flatMap(book -> book.tags().stream())
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .forEach(tags::add);
        return List.copyOf(tags);
    }

    public List<LoadedBook> findAll() {
        return repository.findAll();
    }

    private boolean matchesText(LoadedBook book, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        return contains(book.title(), needle) || contains(book.author(), needle);
    }

    private boolean matchesDifficulty(LoadedBook book, Set<Difficulty> difficulties) {
        if (difficulties == null || difficulties.isEmpty()) {
            return true;
        }
        return book.difficulty() != null && difficulties.contains(book.difficulty());
    }

    private boolean matchesTags(LoadedBook book, Set<String> selectedTags) {
        if (selectedTags == null || selectedTags.isEmpty()) {
            return true;
        }
        return book.tags().stream()
                .anyMatch(tag -> selectedTags.stream().anyMatch(selected ->
                        selected != null && tag.equalsIgnoreCase(selected)));
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }
}
