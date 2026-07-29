package com.adventurebook.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins down the state of the four books shipped with the assessment.
 *
 * <p>Every one of them is unplayable, and each fails for a different reason. These
 * assertions are the regression net for the loader and the validator: if a future change
 * quietly starts accepting a broken book, or stops explaining why, this test fails.
 */
class SuppliedBooksTest {

    private static final Path FIXTURES = Path.of("src/test/resources/books");

    private BookRepository repository;

    @BeforeEach
    void loadSuppliedBooks() {
        repository = new BookRepository(FIXTURES, new BookJsonMapper(), new ValidationEngine());
        repository.reload();
    }

    private LoadedBook book(String slug) {
        return repository.findBySlug(slug).orElseThrow(() -> new AssertionError("missing fixture: " + slug));
    }

    @Test
    @DisplayName("all four supplied books are listed, and none of them can be played")
    void everySuppliedBookIsListedButUnplayable() {
        List<LoadedBook> books = repository.findAll();

        assertThat(books).extracting(LoadedBook::slug).containsExactly(
                "crystal-caverns", "dragon-quest", "pirates-jade-sea", "the-prisoner");
        assertThat(books).allSatisfy(book -> assertThat(book.isPlayable()).isFalse());
    }

    @Test
    @DisplayName("dragon-quest.json is an empty file, so it never becomes a book")
    void dragonQuestIsUnreadable() {
        LoadedBook book = book("dragon-quest");

        assertThat(book.content()).isEmpty();
        assertThat(book.title()).isEqualTo("dragon-quest");
        assertThat(book.sectionCount()).isZero();
        assertThat(book.report().issues()).singleElement()
                .satisfies(issue -> assertThat(issue.rule()).isEqualTo(ValidationRule.UNREADABLE));
    }

    @Test
    @DisplayName("the-prisoner.json only breaks on its orphaned section 666")
    void thePrisonerFailsOnlyOnItsDeadEnd() {
        LoadedBook book = book("the-prisoner");

        assertThat(book.title()).isEqualTo("The Prisoner");
        assertThat(book.difficulty()).isEqualTo(Difficulty.HARD);
        assertThat(book.report().issues()).singleElement().satisfies(issue -> {
            assertThat(issue.rule()).isEqualTo(ValidationRule.NO_DEAD_ENDS);
            assertThat(issue.sectionId()).isEqualTo("666");
        });
    }

    @Test
    @DisplayName("the-prisoner.json mixes id types, and the reference still resolves")
    void thePrisonerMixedIdentifiersDoNotProduceBrokenReferences() {
        Book content = book("the-prisoner").content().orElseThrow();

        // Declared as "id": "500", reached from section 1 via "gotoId": 500.
        Option towardsTheDoor = content.section("1").orElseThrow().options().getFirst();
        assertThat(towardsTheDoor.gotoId()).isEqualTo("500");
        assertThat(content.section("500")).isPresent();
        assertThat(book("the-prisoner").report().issues())
                .noneMatch(issue -> issue.rule() == ValidationRule.VALID_REFERENCES);
    }

    @Test
    @DisplayName("crystal-caverns.json traps the player in section 666")
    void crystalCavernsFailsOnItsReachableDeadEnd() {
        LoadedBook book = book("crystal-caverns");

        assertThat(book.title()).isEqualTo("The Crystal Caverns");
        assertThat(book.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(book.report().issues()).singleElement().satisfies(issue -> {
            assertThat(issue.rule()).isEqualTo(ValidationRule.NO_DEAD_ENDS);
            assertThat(issue.sectionId()).isEqualTo("666");
        });

        // Unlike the-prisoner, this dead end is reachable: section 900 leads straight into it.
        Book content = book.content().orElseThrow();
        assertThat(content.section("900").orElseThrow().options())
                .anyMatch(option -> "666".equals(option.gotoId()));
    }

    @Test
    @DisplayName("pirates-jade-sea.json breaks both of its opening choices")
    void piratesOfTheJadeSeaFailsOnBothOpeningChoices() {
        LoadedBook book = book("pirates-jade-sea");

        assertThat(book.title()).isEqualTo("Pirates of the Jade Sea");
        assertThat(book.difficulty()).isEqualTo(Difficulty.MEDIUM);

        // Neither choice offered on the first page leads anywhere playable: one points at
        // a section that was never written, the other at a section with no way out.
        assertThat(book.report().issues()).hasSize(2);
        assertThat(book.report().issues()).anySatisfy(issue -> {
            assertThat(issue.rule()).isEqualTo(ValidationRule.VALID_REFERENCES);
            assertThat(issue.sectionId()).isEqualTo("1");
            assertThat(issue.message()).contains("999");
        });
        assertThat(book.report().issues()).anySatisfy(issue -> {
            assertThat(issue.rule()).isEqualTo(ValidationRule.NO_DEAD_ENDS);
            assertThat(issue.sectionId()).isEqualTo("666");
        });

        Book content = book.content().orElseThrow();
        assertThat(content.section("1").orElseThrow().options())
                .extracting(Option::gotoId)
                .containsExactly("666", "999");
        // Section 20 reads like the intended destination of that second choice, but nothing
        // references it, so the story can never reach it.
        assertThat(content.section("20")).isPresent();
    }

    @Test
    @DisplayName("book-level metadata that is not part of the model is ignored")
    void unknownBookPropertiesDoNotPreventLoading() {
        // pirates-jade-sea.json carries a stray top-level "type": "" alongside its sections.
        assertThat(book("pirates-jade-sea").content()).isPresent();
    }
}
