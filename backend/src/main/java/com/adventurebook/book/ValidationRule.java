package com.adventurebook.book;

/**
 * Reasons a book can be rejected.
 *
 * <p>The first four mirror the rules stated in the brief. {@link #UNIQUE_IDS} is an
 * addition: without it {@code gotoId} resolution would be ambiguous, so a book with
 * repeated identifiers cannot be played deterministically. {@link #UNREADABLE} covers
 * files that never became a book at all.
 */
public enum ValidationRule {
    UNREADABLE("The file could not be read as an adventure book"),
    UNIQUE_BEGIN("A book must have exactly one BEGIN section"),
    HAS_END("A book must have at least one END section"),
    VALID_REFERENCES("Every choice must point at a section that exists"),
    NO_DEAD_ENDS("Every non-ending section must offer at least one choice"),
    UNIQUE_IDS("Section identifiers must be unique");

    private final String description;

    ValidationRule(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
