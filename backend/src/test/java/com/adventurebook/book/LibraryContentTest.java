package com.adventurebook.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the boundary between the supplied catalogue and our optional upload samples.
 *
 * <p>The application starts with exactly the four assessment files, all unplayable for the
 * independently verified reasons pinned by {@link SuppliedBooksTest}. The two original books
 * live outside that catalogue so they can be uploaded during a demo; the assertions below
 * keep those samples playable and exercising every required mechanic.
 */
class LibraryContentTest {

    private static final Path SUPPLIED_BOOKS = Path.of("../books");
    private static final Path UPLOAD_SAMPLES = Path.of("../upload-samples");

    private BookRepository repository;

    @BeforeAll
    static void booksDirectoryIsWhereWeThinkItIs() {
        assertThat(SUPPLIED_BOOKS).isDirectory();
        assertThat(UPLOAD_SAMPLES).isDirectory();
    }

    @BeforeEach
    void loadLibrary() {
        repository = new BookRepository(UPLOAD_SAMPLES, new BookJsonMapper(), new ValidationEngine());
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
    @DisplayName("the committed catalogue is exactly the four supplied, unplayable books")
    void initialCatalogueContainsOnlySuppliedBooks() throws IOException {
        // Guard what actually ships — the git-tracked files — not the live directory. Uploading
        // a book while testing locally writes a gitignored file into books/; that must never fail
        // this invariant. Falls back to the on-disk scan when git is absent (e.g. a downloaded
        // ZIP rather than a clone), where a clean tree holds the same four files anyway.
        List<String> committed = committedBookSlugs();
        assertThat(committed)
                .containsExactly("crystal-caverns", "dragon-quest", "pirates-jade-sea", "the-prisoner");

        BookRepository supplied = new BookRepository(SUPPLIED_BOOKS, new BookJsonMapper(), new ValidationEngine());
        supplied.reload();
        assertThat(committed).allSatisfy(slug ->
                assertThat(supplied.findBySlug(slug)).get()
                        .matches(book -> !book.isPlayable(), slug + " must be unplayable"));
    }

    /** Book slugs git tracks under books/ — what actually ships — or the disk scan if git is absent. */
    private static List<String> committedBookSlugs() throws IOException {
        List<String> names = gitTrackedBookFileNames();
        if (names == null) {
            try (Stream<Path> files = Files.list(SUPPLIED_BOOKS)) {
                names = files.map(path -> path.getFileName().toString()).toList();
            }
        }
        return names.stream()
                .filter(name -> name.endsWith(".json"))
                .map(name -> name.substring(0, name.length() - ".json".length()))
                .sorted()
                .toList();
    }

    /** {@code git ls-files books} as bare filenames, or {@code null} when git is unavailable. */
    private static List<String> gitTrackedBookFileNames() {
        try {
            Process process = new ProcessBuilder("git", "-C", "..", "ls-files", "books").start();
            List<String> lines;
            try (var reader = process.inputReader()) {
                lines = reader.lines().map(line -> line.substring(line.lastIndexOf('/') + 1)).toList();
            }
            return process.waitFor() == 0 && !lines.isEmpty() ? lines : null;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Test
    @DisplayName("the upload samples contain exactly the two playable books we wrote")
    void onlyOurOwnSamplesArePlayable() {
        List<LoadedBook> playable = repository.findAll().stream().filter(LoadedBook::isPlayable).toList();

        assertThat(playable).extracting(LoadedBook::slug)
                .containsExactly("clockwork-lighthouse", "sunken-orchard");
        assertThat(repository.findAll()).hasSize(2);
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
