package com.adventurebook.api;

import java.util.List;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.adventurebook.api.dto.BookSummary;
import com.adventurebook.book.BookNotFoundException;
import com.adventurebook.book.BookRepository;
import com.adventurebook.book.BookService;
import com.adventurebook.book.Difficulty;
import com.adventurebook.save.SaveService;

/** Read access to the library. */
@RestController
@RequestMapping(path = "/api/books", produces = MediaType.APPLICATION_JSON_VALUE)
public class BookController {

    private final BookService bookService;
    private final BookRepository bookRepository;
    private final SaveService saves;

    public BookController(BookService bookService, BookRepository bookRepository, SaveService saves) {
        this.bookService = bookService;
        this.bookRepository = bookRepository;
        this.saves = saves;
    }

    /**
     * Lists the library, optionally narrowed by free text and difficulty.
     *
     * <p>Filtering happens here rather than in the browser so every client behaves the
     * same way and the frontend stays a rendering layer.
     */
    @GetMapping
    public List<BookSummary> list(@RequestParam(required = false) String query,
            @RequestParam(required = false) Set<Difficulty> difficulty) {
        // Fetched once for the whole page rather than per card.
        Set<String> saved = saves.slugsWithSavedProgress();
        return bookService.search(query, difficulty).stream()
                .map(book -> BookSummary.from(book, saved.contains(book.slug())))
                .toList();
    }

    @GetMapping("/{slug}")
    public BookSummary get(@PathVariable String slug) {
        return bookRepository.findBySlug(slug)
                .map(book -> BookSummary.from(book, saves.exists(slug)))
                .orElseThrow(() -> new BookNotFoundException(slug));
    }
}
