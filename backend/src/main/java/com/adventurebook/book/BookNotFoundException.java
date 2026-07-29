package com.adventurebook.book;

/** No book in the library carries the requested slug. */
public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(String slug) {
        super("No book with slug '%s'".formatted(slug));
    }
}
