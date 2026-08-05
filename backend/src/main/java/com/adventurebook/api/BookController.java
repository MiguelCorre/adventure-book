package com.adventurebook.api;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.adventurebook.api.dto.BookSummary;
import com.adventurebook.book.BookNotFoundException;
import com.adventurebook.book.BookRepository;
import com.adventurebook.book.BookService;
import com.adventurebook.book.BookUploadService;
import com.adventurebook.book.Difficulty;
import com.adventurebook.save.SaveService;

/** Read access to the library. */
@RestController
@RequestMapping(path = "/api/books", produces = MediaType.APPLICATION_JSON_VALUE)
public class BookController {

    private final BookService bookService;
    private final BookRepository bookRepository;
    private final BookUploadService uploads;
    private final SaveService saves;

    public BookController(BookService bookService, BookRepository bookRepository,
            BookUploadService uploads, SaveService saves) {
        this.bookService = bookService;
        this.bookRepository = bookRepository;
        this.uploads = uploads;
        this.saves = saves;
    }

    /**
     * Lists the library, optionally narrowed by free text, difficulty and tags.
     *
     * <p>Filtering happens here rather than in the browser so every client behaves the
     * same way and the frontend stays a rendering layer.
     */
    @GetMapping
    public List<BookSummary> list(@RequestParam(required = false) String query,
            @RequestParam(required = false) Set<Difficulty> difficulty,
            @RequestParam(required = false) Set<String> tag) {
        // Fetched once for the whole page rather than per card.
        Set<String> saved = saves.slugsWithSavedProgress();
        return bookService.search(query, difficulty, tag).stream()
                .map(book -> BookSummary.from(book, saved.contains(book.slug())))
                .toList();
    }

    @GetMapping("/tags")
    public List<String> tags() {
        return bookService.tags();
    }

    @GetMapping("/{slug}")
    public BookSummary get(@PathVariable String slug) {
        return bookRepository.findBySlug(slug)
                .map(book -> BookSummary.from(book, saves.exists(slug)))
                .orElseThrow(() -> new BookNotFoundException(slug));
    }

    /**
     * Adds a book to the library.
     *
     * <p>A book that fails validation is rejected with the full report attached, and
     * nothing is written, so the library is never left holding a story nobody can finish.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BookSummary upload(@RequestPart("file") MultipartFile file) throws IOException {
        return BookSummary.from(uploads.add(file.getBytes()), false);
    }

    /**
     * Forgets the saved progress for a book.
     *
     * <p>Idempotent on purpose: deleting a slot that is already empty is a no-op, not an
     * error, so repeating the request cannot change the outcome. The slug is not checked
     * against the library either — a save may outlive its book if the file is removed from
     * the directory, and this must still be able to clean it up.
     */
    @DeleteMapping("/{slug}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discardSave(@PathVariable String slug) {
        saves.discard(slug);
    }
}
