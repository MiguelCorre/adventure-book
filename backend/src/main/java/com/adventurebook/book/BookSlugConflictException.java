package com.adventurebook.book;

/** A book with the same derived slug is already in the library. */
public class BookSlugConflictException extends RuntimeException {

    public BookSlugConflictException(String slug) {
        super("The library already contains a book called '%s'".formatted(slug));
    }
}
