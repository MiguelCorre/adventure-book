package com.adventurebook.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the books that ship with the application.
 *
 * <p>Because every supplied book is unplayable, the library needs content of its own or
 * none of the game could be demonstrated. These two are written for that purpose and the
 * assertions below keep them honest: playable, and still exercising every mechanic the
 * brief asks for.
 */
class LibraryContentTest {

    private static final Path SHIPPED_BOOKS = Path.of("../books");

    private BookRepository repository;

    @BeforeAll
    static void booksDirectoryIsWhereWeThinkItIs() {
        assertThat(Files.isDirectory(SHIPPED_BOOKS))
                .as("books directory resolved from the backend working directory")
                .isTrue();
    }

    @BeforeEach
    void loadLibrary() {
        repository = new BookRepository(SHIPPED_BOOKS, new BookJsonMapper(), new ValidationEngine());
        repository.reload();
    }

    private LoadedBook book(String slug) {
        return repository.findBySlug(slug).orElseThrow(() -> new AssertionError("missing book: " + slug));
    }

    /** Walks the book by taking the option whose description is given, applying its cost. */
    private int healthAfter(Book book, String startId, int startingHealth, String... choices) {
        int health = startingHealth;
        String sectionId = startId;
        for (String choice : choices) {
            Section section = book.section(sectionId).orElseThrow(
                    () -> new AssertionError("no section " + startId));
            Option option = section.options().stream()
                    .filter(o -> o.description().equals(choice))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no option \"" + choice + "\" in section " + section.id()));
            Consequence consequence = option.consequence();
            if (consequence != null) {
                health += consequence.type() == ConsequenceType.LOSE_HEALTH
                        ? -consequence.value()
                        : consequence.value();
            }
            sectionId = option.gotoId();
        }
        return health;
    }

    @Test
    @DisplayName("the library contains exactly the two playable books we wrote")
    void onlyOurOwnBooksArePlayable() {
        List<LoadedBook> playable = repository.findAll().stream().filter(LoadedBook::isPlayable).toList();

        assertThat(playable).extracting(LoadedBook::slug)
                .containsExactly("clockwork-lighthouse", "sunken-orchard");
        assertThat(repository.findAll()).hasSize(6);
    }

    @Test
    void theClockworkLighthouseIsPlayableAndAdvertisedCorrectly() {
        LoadedBook lighthouse = book("clockwork-lighthouse");

        assertThat(lighthouse.report().issues()).isEmpty();
        assertThat(lighthouse.title()).isEqualTo("The Clockwork Lighthouse");
        assertThat(lighthouse.difficulty()).isEqualTo(Difficulty.MEDIUM);
        assertThat(lighthouse.content().orElseThrow().sectionsOfType(SectionType.END))
                .as("more than one way to finish").hasSizeGreaterThan(1);
    }

    @Test
    void theSunkenOrchardIsPlayableAndAdvertisedCorrectly() {
        LoadedBook orchard = book("sunken-orchard");

        assertThat(orchard.report().issues()).isEmpty();
        assertThat(orchard.title()).isEqualTo("The Sunken Orchard");
        assertThat(orchard.difficulty()).isEqualTo(Difficulty.EASY);
    }

    @Test
    @DisplayName("the lighthouse can be finished without taking a single wound")
    void offersAConsequenceFreeRouteToAnEnding() {
        Book lighthouse = book("clockwork-lighthouse").content().orElseThrow();

        int health = healthAfter(lighthouse, "1", 10,
                "Take the outer stair that spirals up the tower",
                "Keep climbing, close to the wall",
                "Duck through the open window",
                "Fetch the crank handle and wind the mechanism properly");

        assertThat(health).as("no damage taken on the safe route").isEqualTo(10);
    }

    @Test
    @DisplayName("the lighthouse has a route that kills a full-health player")
    void offersARouteWhoseDamageExceedsAFullHealthBar() {
        Book lighthouse = book("clockwork-lighthouse").content().orElseThrow();

        int health = healthAfter(lighthouse, "1", 10,
                "Force the seaward door at the base",
                "Wade straight through to the ladder",
                "Pry the jammed gear loose with your hands, there is no time");

        assertThat(health).as("13 damage against 10 health").isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("the lighthouse has a heal large enough to prove the health ceiling")
    void offersAHealThatWouldOvershootFullHealth() {
        Book lighthouse = book("clockwork-lighthouse").content().orElseThrow();

        // Wounded to 6 by the door, then offered +6: enough to push past 10 if uncapped.
        int health = healthAfter(lighthouse, "1", 10,
                "Force the seaward door at the base",
                "Edge along the wall to the workbench",
                "Open the tin and see to yourself before going on");

        assertThat(health).as("raw arithmetic overshoots the ceiling").isGreaterThan(10);
    }
}
