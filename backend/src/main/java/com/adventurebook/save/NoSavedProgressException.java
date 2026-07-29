package com.adventurebook.save;

/** A continue was requested for a book that has never been saved. */
public class NoSavedProgressException extends RuntimeException {

    public NoSavedProgressException(String bookSlug) {
        super("No saved progress for book '%s'".formatted(bookSlug));
    }
}
