package com.adventurebook.book;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads book JSON into the domain model.
 *
 * <p>Owns its own {@link JsonMapper} rather than borrowing the application-wide one so
 * that the parsing rules applied to untrusted book files are identical in production and
 * in tests, and cannot drift when the HTTP layer's serialisation settings change.
 *
 * <p>Unknown properties are tolerated: a book carrying extra metadata is still a book.
 * Everything else is strict — a malformed enum, a non-numeric consequence value or a
 * broken document raises {@link BookParseException}, which callers turn into a reported
 * validation issue instead of a failed startup.
 */
@Component
public class BookJsonMapper {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public Book read(byte[] json) {
        if (json == null || json.length == 0) {
            throw new BookParseException("file is empty");
        }
        try {
            Book book = mapper.readValue(json, Book.class);
            if (book == null) {
                throw new BookParseException("file does not contain a book object");
            }
            return book;
        } catch (JacksonException e) {
            throw new BookParseException(describe(e), e);
        } catch (IllegalArgumentException e) {
            throw new BookParseException(e.getMessage(), e);
        }
    }

    public Book read(Path path) {
        try {
            return read(Files.readAllBytes(path));
        } catch (BookParseException e) {
            throw new BookParseException(path.getFileName() + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new BookParseException("file could not be read: " + e.getMessage(), e);
        }
    }

    /** Keep the stable, actionable parts of Jackson's diagnostic without exposing source content. */
    private static String describe(JacksonException e) {
        String message = e.getCause() instanceof IllegalArgumentException cause
                ? cause.getMessage()
                : e.getOriginalMessage();
        String summary = message == null || message.isBlank() ? "file is not valid JSON" : message;
        StringJoiner context = new StringJoiner(", ");

        String path = jsonPath(e);
        if (!path.isEmpty()) {
            context.add("path " + path);
        }
        if (e.getLocation() != null && e.getLocation().getLineNr() > 0) {
            context.add("line %d, column %d".formatted(
                    e.getLocation().getLineNr(), e.getLocation().getColumnNr()));
        }

        return context.length() == 0 ? summary : "%s (%s)".formatted(summary, context);
    }

    private static String jsonPath(JacksonException exception) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : exception.getPath()) {
            if (reference.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
