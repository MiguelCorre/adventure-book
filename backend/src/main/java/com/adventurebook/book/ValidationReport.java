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

    public static ValidationReport unreadable(String reason) {
        return new ValidationReport(List.of(ValidationIssue.of(ValidationRule.UNREADABLE, reason)));
    }

    public boolean isValid() {
        return issues.isEmpty();
    }
}
