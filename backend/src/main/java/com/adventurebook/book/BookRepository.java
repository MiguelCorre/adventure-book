package com.adventurebook.book;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.adventurebook.config.AdventureProperties;

import jakarta.annotation.PostConstruct;

/**
 * Loads book files from the configured directory and keeps them in memory.
 *
 * <p>Books are read once at startup and re-read on demand after an upload. The directory
 * is not watched: reloading is an explicit, observable action, which keeps behaviour
 * predictable and avoids a background thread reacting to half-written files.
 *
 * <p>A file that cannot be parsed does not stop the scan. It is kept as an unreadable
 * {@link LoadedBook} so that the reason reaches the user.
 */
@Repository
public class BookRepository {

    private static final Logger log = LoggerFactory.getLogger(BookRepository.class);
    private static final String EXTENSION = ".json";

    private final Path directory;
    private final BookJsonMapper jsonMapper;
    private final ValidationEngine validationEngine;

    private volatile Map<String, LoadedBook> booksBySlug = Map.of();

    @Autowired
    public BookRepository(AdventureProperties properties, BookJsonMapper jsonMapper,
            ValidationEngine validationEngine) {
        this(properties.booksPath(), jsonMapper, validationEngine);
    }

    /** Test seam: point the repository at an arbitrary directory without a Spring context. */
    BookRepository(Path directory, BookJsonMapper jsonMapper, ValidationEngine validationEngine) {
        this.directory = directory;
        this.jsonMapper = jsonMapper;
        this.validationEngine = validationEngine;
    }

    @PostConstruct
    public void reload() {
        Map<String, LoadedBook> loaded = new LinkedHashMap<>();
        if (!Files.isDirectory(directory)) {
            log.warn("Books directory {} does not exist; the library will be empty",
                    directory.toAbsolutePath());
            booksBySlug = Map.of();
            return;
        }

        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> {
                        LoadedBook book = load(path);
                        loaded.put(book.slug(), book);
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list books in " + directory.toAbsolutePath(), e);
        }

        // Deliberately not Map.copyOf: that returns an unordered map and would throw away
        // the alphabetical order the scan just established.
        booksBySlug = Collections.unmodifiableMap(loaded);
        log.info("Loaded {} book(s) from {}, {} playable", loaded.size(), directory.toAbsolutePath(),
                loaded.values().stream().filter(LoadedBook::isPlayable).count());
    }

    private LoadedBook load(Path path) {
        String slug = slugOf(path);
        try {
            Book book = jsonMapper.read(path);
            return new LoadedBook(slug, book, validationEngine.validate(book));
        } catch (BookParseException e) {
            log.warn("Book {} could not be read: {}", slug, e.getMessage());
            return LoadedBook.unreadable(slug, e.getMessage());
        }
    }

    public List<LoadedBook> findAll() {
        return List.copyOf(booksBySlug.values());
    }

    public Optional<LoadedBook> findBySlug(String slug) {
        return Optional.ofNullable(booksBySlug.get(slug));
    }

    public boolean exists(String slug) {
        return booksBySlug.containsKey(slug);
    }

    public Path directory() {
        return directory;
    }

    static String slugOf(Path path) {
        String name = path.getFileName().toString();
        return name.regionMatches(true, name.length() - EXTENSION.length(), EXTENSION, 0, EXTENSION.length())
                ? name.substring(0, name.length() - EXTENSION.length())
                : name;
    }
}
