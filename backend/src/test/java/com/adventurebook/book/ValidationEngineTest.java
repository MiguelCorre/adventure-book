package com.adventurebook.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.adventurebook.book.testsupport.Books;

class ValidationEngineTest {

    private final ValidationEngine engine = new ValidationEngine();

    private List<ValidationRule> rulesBrokenBy(Book book) {
        return engine.validate(book).issues().stream().map(ValidationIssue::rule).distinct().toList();
    }

    @Nested
    @DisplayName("accepts playable books")
    class Valid {

        @Test
        void acceptsAMinimalBeginToEndBook() {
            Book book = Books.of(
                    Books.begin("1", Books.goTo("Escape", "2")),
                    Books.end("2"));

            assertThat(engine.validate(book).isValid()).isTrue();
            assertThat(engine.validate(book).issues()).isEmpty();
        }

        @Test
        void ignoresOptionalPresentationMetadata() {
            Book withoutMetadata = Books.of(
                    Books.begin("1", Books.goTo("Escape", "2")),
                    Books.end("2"));
            Book withMetadata = new Book(
                    withoutMetadata.title(),
                    withoutMetadata.author(),
                    "A short escape story.",
                    List.of("Escape"),
                    withoutMetadata.difficulty(),
                    List.of(
                            new Section("1", "The Cell", "Section 1", SectionType.BEGIN,
                                    List.of(Books.goTo("Escape", "2"))),
                            new Section("2", "Outside", "Section 2", SectionType.END, List.of())));

            assertThat(engine.validate(withMetadata)).isEqualTo(engine.validate(withoutMetadata));
            assertThat(engine.validate(withMetadata).isValid()).isTrue();
        }

        @Test
        void allowsSeveralEndings() {
            Book book = Books.of(
                    Books.begin("1", Books.goTo("Left", "2"), Books.goTo("Right", "3")),
                    Books.end("2"),
                    Books.end("3"));

            assertThat(engine.validate(book).isValid()).isTrue();
        }

        @Test
        void allowsAnEndingThatStillDeclaresOptions() {
            Book book = Books.of(
                    Books.begin("1", Books.goTo("On", "2")),
                    new Section("2", "The end.", SectionType.END, List.of(Books.goTo("Again", "1"))));

            assertThat(engine.validate(book).isValid()).isTrue();
        }

        @Test
        void allowsSectionsThatAreUnreachableButStillOfferChoices() {
            // Reachability is not one of the stated rules; only structural soundness is.
            Book book = Books.of(
                    Books.begin("1", Books.goTo("On", "2")),
                    Books.end("2"),
                    Books.node("99", Books.goTo("Back", "1")));

            assertThat(engine.validate(book).isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("rejects books with no single beginning")
    class Beginnings {

        @Test
        void rejectsABookWithoutABeginning() {
            Book book = Books.of(Books.node("1", Books.goTo("On", "2")), Books.end("2"));

            assertThat(rulesBrokenBy(book)).contains(ValidationRule.UNIQUE_BEGIN);
            assertThat(engine.validate(book).issues())
                    .anyMatch(issue -> issue.message().contains("no BEGIN section"));
        }

        @Test
        void rejectsABookWithTwoBeginnings() {
            Book book = Books.of(
                    Books.begin("1", Books.goTo("On", "3")),
                    Books.begin("2", Books.goTo("On", "3")),
                    Books.end("3"));

            assertThat(rulesBrokenBy(book)).contains(ValidationRule.UNIQUE_BEGIN);
            assertThat(engine.validate(book).issues())
                    .anyMatch(issue -> issue.message().contains("2 BEGIN sections"));
        }
    }

    @Nested
    @DisplayName("rejects books that cannot be finished or navigated")
    class Structure {

        @Test
        void rejectsABookWithoutAnEnding() {
            Book book = Books.of(
                    Books.begin("1", Books.goTo("On", "2")),
                    Books.node("2", Books.goTo("Back", "1")));

            assertThat(rulesBrokenBy(book)).contains(ValidationRule.HAS_END);
        }

        @Test
        void rejectsAChoicePointingAtAMissingSection() {
            Book book = Books.of(
                    Books.begin("1", Books.goTo("Into the void", "404")),
                    Books.end("2"));

            ValidationReport report = engine.validate(book);
            assertThat(report.isValid()).isFalse();
            assertThat(report.issues())
                    .anyMatch(issue -> issue.rule() == ValidationRule.VALID_REFERENCES
                            && "1".equals(issue.sectionId())
                            && issue.message().contains("404"));
        }

        @Test
        void rejectsANonEndingSectionWithNoChoices() {
            Book book = Books.of(
                    Books.begin("1", Books.goTo("On", "2")),
                    Books.end("2"),
                    Books.node("666"));

            ValidationReport report = engine.validate(book);
            assertThat(report.issues())
                    .anyMatch(issue -> issue.rule() == ValidationRule.NO_DEAD_ENDS
                            && "666".equals(issue.sectionId()));
        }

        @Test
        void rejectsDuplicateSectionIdentifiers() {
            Book book = Books.of(
                    Books.begin("1", Books.goTo("On", "2")),
                    Books.end("2"),
                    Books.end("2"));

            assertThat(rulesBrokenBy(book)).contains(ValidationRule.UNIQUE_IDS);
        }

        @Test
        void reportsADuplicateIdentifierOnlyOnce() {
            Book book = Books.of(
                    Books.begin("1", Books.goTo("On", "2")),
                    Books.end("2"),
                    Books.end("2"),
                    Books.end("2"));

            assertThat(engine.validate(book).issues())
                    .filteredOn(issue -> issue.rule() == ValidationRule.UNIQUE_IDS)
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("reports every problem at once")
    class Completeness {

        @Test
        void collectsAllBrokenRulesRatherThanStoppingAtTheFirst() {
            Book book = Books.of(
                    Books.node("1", Books.goTo("Nowhere", "999")),
                    Books.node("2"));

            assertThat(rulesBrokenBy(book)).containsExactlyInAnyOrder(
                    ValidationRule.UNIQUE_BEGIN,
                    ValidationRule.HAS_END,
                    ValidationRule.VALID_REFERENCES,
                    ValidationRule.NO_DEAD_ENDS);
        }

        @Test
        void treatsAnAbsentBookAsUnreadable() {
            ValidationReport report = engine.validate(null);

            assertThat(report.isValid()).isFalse();
            assertThat(report.issues()).singleElement()
                    .satisfies(issue -> assertThat(issue.rule()).isEqualTo(ValidationRule.UNREADABLE));
        }
    }
}
