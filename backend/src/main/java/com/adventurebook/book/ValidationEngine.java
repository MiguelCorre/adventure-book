package com.adventurebook.book;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Decides whether a book can be played.
 *
 * <p>Reports every problem it finds rather than stopping at the first one: the library
 * shows the player exactly what is wrong with a book, and a curator uploading a file
 * should get the whole list back in one go.
 *
 * <p>All four rules from the brief are hard failures, plus identifier uniqueness without
 * which choices could not be resolved deterministically.
 */
@Component
public class ValidationEngine {

    public ValidationReport validate(Book book) {
        if (book == null) {
            return ValidationReport.unreadable("No book content");
        }

        List<ValidationIssue> issues = new ArrayList<>();
        checkUniqueIdentifiers(book, issues);
        checkSingleBeginning(book, issues);
        checkHasEnding(book, issues);
        checkReferencesResolve(book, issues);
        checkNoDeadEnds(book, issues);
        return new ValidationReport(issues);
    }

    private void checkUniqueIdentifiers(Book book, List<ValidationIssue> issues) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> reported = new LinkedHashSet<>();
        for (Section section : book.sections()) {
            String id = section.id();
            if (id == null) {
                issues.add(ValidationIssue.of(ValidationRule.UNIQUE_IDS, "A section has no identifier"));
            } else if (!seen.add(id) && reported.add(id)) {
                issues.add(ValidationIssue.at(ValidationRule.UNIQUE_IDS, id,
                        "Section %s is declared more than once".formatted(id)));
            }
        }
    }

    private void checkSingleBeginning(Book book, List<ValidationIssue> issues) {
        List<Section> beginnings = book.sectionsOfType(SectionType.BEGIN);
        if (beginnings.isEmpty()) {
            issues.add(ValidationIssue.of(ValidationRule.UNIQUE_BEGIN,
                    "The book has no BEGIN section, so there is nowhere to start"));
        } else if (beginnings.size() > 1) {
            String ids = beginnings.stream().map(Section::id).map(String::valueOf).reduce((a, b) -> a + ", " + b)
                    .orElse("");
            issues.add(ValidationIssue.of(ValidationRule.UNIQUE_BEGIN,
                    "The book has %d BEGIN sections (%s); exactly one is required"
                            .formatted(beginnings.size(), ids)));
        }
    }

    private void checkHasEnding(Book book, List<ValidationIssue> issues) {
        if (book.sectionsOfType(SectionType.END).isEmpty()) {
            issues.add(ValidationIssue.of(ValidationRule.HAS_END,
                    "The book has no END section, so the adventure can never be finished"));
        }
    }

    private void checkReferencesResolve(Book book, List<ValidationIssue> issues) {
        Set<String> knownIds = book.sectionsById().keySet();
        for (Section section : book.sections()) {
            for (Option option : section.options()) {
                String target = option.gotoId();
                if (target == null) {
                    issues.add(ValidationIssue.at(ValidationRule.VALID_REFERENCES, section.id(),
                            "Choice \"%s\" in section %s has no destination"
                                    .formatted(option.description(), section.id())));
                } else if (!knownIds.contains(target)) {
                    issues.add(ValidationIssue.at(ValidationRule.VALID_REFERENCES, section.id(),
                            "Choice \"%s\" in section %s points at section %s, which does not exist"
                                    .formatted(option.description(), section.id(), target)));
                }
            }
        }
    }

    private void checkNoDeadEnds(Book book, List<ValidationIssue> issues) {
        for (Section section : book.sections()) {
            if (!section.isEnding() && !section.hasOptions()) {
                issues.add(ValidationIssue.at(ValidationRule.NO_DEAD_ENDS, section.id(),
                        "Section %s is not an ending but offers no choices, trapping the player"
                                .formatted(section.id())));
            }
        }
    }
}
