package com.adventurebook.book;

/** An uploaded book broke the rules and was not stored. Carries the full report. */
public class BookRejectedException extends RuntimeException {

    private final transient ValidationReport report;

    public BookRejectedException(ValidationReport report) {
        super("The uploaded book is not valid and was not added to the library");
        this.report = report;
    }

    public ValidationReport report() {
        return report;
    }
}
