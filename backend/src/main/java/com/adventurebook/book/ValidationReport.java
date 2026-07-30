package com.adventurebook.book;

import java.util.List;

/**
 * Outcome of validating one book.
 *
 * @param issues every problem found, in rule order; empty when the book is playable
 */
public record ValidationReport(List<ValidationIssue> issues) {

    private static final ValidationReport VALID = new ValidationReport(List.of());

    public ValidationReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static ValidationReport valid() {
        return VALID;
    }

    /**
     * @param reason the underlying parse failure, folded into a sentence so the library
     *               shows the reader a complete explanation rather than a bare fragment
     */
    public static ValidationReport unreadable(String reason) {
        String message = "%s: %s".formatted(ValidationRule.UNREADABLE.description(), reason);
        return new ValidationReport(List.of(ValidationIssue.of(ValidationRule.UNREADABLE, message)));
    }

    public boolean isValid() {
        return issues.isEmpty();
    }
}
