package com.adventurebook.book;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Adds a book to the library from an uploaded file.
 *
 * <p>Nothing is written until the book has parsed and passed every validation rule, so a
 * rejected upload leaves the library exactly as it was and the curator gets the whole
 * list of problems back in one response.
 *
 * <p>Uploads are serialized so writing the file and publishing the reloaded catalogue
 * form one transaction. Otherwise an older concurrent reload could overwrite the cache
 * snapshot produced by a newer upload.
 *
 * <p>The filename on disk is derived from the book's own title, never from the uploaded
 * filename. A client-supplied name is untrusted input and would be a path traversal
 * waiting to happen; a slug built from an allow-list of characters cannot escape the
 * books directory.
 */
@Service
public class BookUploadService {

    private static final Logger log = LoggerFactory.getLogger(BookUploadService.class);

    private final BookRepository repository;
    private final BookJsonMapper jsonMapper;
    private final ValidationEngine validationEngine;

    public BookUploadService(BookRepository repository, BookJsonMapper jsonMapper,
            ValidationEngine validationEngine) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.validationEngine = validationEngine;
    }

    public LoadedBook add(byte[] content) {
        Book book = parseOrReject(content);

        ValidationReport report = validationEngine.validate(book);
        if (!report.isValid()) {
            throw new BookRejectedException(report);
        }

        String slug = slugOf(book.title());
        return store(slug, content);
    }

    private synchronized LoadedBook store(String slug, byte[] content) {
        if (repository.exists(slug)) {
            throw new BookSlugConflictException(slug);
        }

        write(slug, content);
        repository.reload();
        log.info("Added book '{}' to the library", slug);

        return repository.findBySlug(slug).orElseThrow(() -> new IllegalStateException(
                "Book '%s' was written but did not reload".formatted(slug)));
    }

    private Book parseOrReject(byte[] content) {
        try {
            return jsonMapper.read(content);
        } catch (BookParseException e) {
            throw new BookRejectedException(ValidationReport.unreadable(e.getMessage()));
        }
    }

    private void write(String slug, byte[] content) {
        Path target = repository.directory().resolve(slug + ".json");
        try {
            Files.createDirectories(repository.directory());
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException e) {
            // The cache said the slug was free, so the file appeared underneath us: another
            // upload, or someone dropping a file into the directory. Still the caller's
            // answer, not a server fault.
            throw new BookSlugConflictException(slug);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store book at " + target.toAbsolutePath(), e);
        }
    }

    /**
     * Builds a filesystem-safe slug from a title: accented characters are folded, anything
     * outside {@code [a-z0-9]} becomes a hyphen, and runs collapse.
     */
    static String slugOf(String title) {
        if (title == null || title.isBlank()) {
            throw new BookRejectedException(ValidationReport.unreadable("The book has no title"));
        }

        String folded = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        String slug = folded.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");

        if (slug.isEmpty()) {
            throw new BookRejectedException(
                    ValidationReport.unreadable("The book title cannot be turned into a filename"));
        }
        return slug;
    }

    /** Convenience for callers holding text rather than bytes. */
    public LoadedBook add(String json) {
        return add(json.getBytes(StandardCharsets.UTF_8));
    }
}
