package com.adventurebook.book;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Library queries over the loaded books.
 *
 * <p>Searching and filtering happen here rather than in the browser so the contract is
 * the same for every client and the frontend stays a rendering layer.
 *
 * <p>Invalid books are never hidden. The brief asks the home page to list <em>all</em>
 * books, and a player is better served by seeing a broken book with an explanation than
 * by wondering where it went.
 */
@Service
public class BookService {

    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = repository;
    }

    public List<LoadedBook> search(String query, Set<Difficulty> difficulties) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return repository.findAll().stream()
                .filter(book -> matchesText(book, needle))
                .filter(book -> matchesDifficulty(book, difficulties))
                .toList();
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

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }
}
