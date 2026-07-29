package com.adventurebook.book;

/** Raised when a book file cannot be turned into a {@link Book}. */
public class BookParseException extends RuntimeException {

    public BookParseException(String message) {
        super(message);
    }

    public BookParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
