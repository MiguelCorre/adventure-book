package com.adventurebook.api.dto;

import com.adventurebook.book.ValidationIssue;

/**
 * A validation problem as the library shows it.
 *
 * @param rule      stable machine-readable rule name, for grouping or styling
 * @param message   sentence shown to the user
 * @param sectionId offending section, {@code null} when the problem is book-wide
 */
public record ValidationIssueView(String rule, String message, String sectionId) {

    public static ValidationIssueView from(ValidationIssue issue) {
        return new ValidationIssueView(issue.rule().name(), issue.message(), issue.sectionId());
    }
}
