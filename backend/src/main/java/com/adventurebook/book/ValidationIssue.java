package com.adventurebook.book;

/**
 * A single reason a book is unplayable.
 *
 * @param rule      which rule was broken
 * @param message   human-readable explanation, shown directly in the library
 * @param sectionId section the issue was found in, or {@code null} for book-wide issues
 */
public record ValidationIssue(ValidationRule rule, String message, String sectionId) {

    public static ValidationIssue of(ValidationRule rule, String message) {
        return new ValidationIssue(rule, message, null);
    }

    public static ValidationIssue at(ValidationRule rule, String sectionId, String message) {
        return new ValidationIssue(rule, message, sectionId);
    }
}
