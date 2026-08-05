package com.adventurebook.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BookServiceTest {

    private BookService service;

    @BeforeEach
    void setUp(@TempDir Path books) throws IOException {
        write(books, "crystal-caverns.json", "The Crystal Caverns", "Evelyn Stormrider", "EASY");
        write(books, "dragon-quest.json", "Dragon Quest", "Marcus Flame", "HARD");
        write(books, "the-prisoner.json", "The Prisoner", "Daniel El Fuego", "HARD");
        write(books, "unlabelled.json", "No Difficulty Given", "Anon", null);

        BookRepository repository = new BookRepository(books, new BookJsonMapper(), new ValidationEngine());
        repository.reload();
        service = new BookService(repository);
    }

    private void write(Path directory, String filename, String title, String author, String difficulty)
            throws IOException {
        String difficultyField = difficulty == null ? "" : "\"difficulty\": \"%s\", ".formatted(difficulty);
        String tags = switch (filename) {
            case "crystal-caverns.json" -> "\"tags\": [\"Steampunk\"], ";
            case "dragon-quest.json" -> "\"tags\": [\"Mystery\"], ";
            case "the-prisoner.json" -> "\"tags\": [\"Mystery\", \"Coastal\"], ";
            default -> "\"tags\": [], ";
        };
        Files.writeString(directory.resolve(filename), """
                { "title": "%s", "author": "%s", %s %s "sections": [
                  { "id": 1, "text": "Start.", "type": "BEGIN",
                    "options": [ { "description": "Finish", "gotoId": 2 } ] },
                  { "id": 2, "text": "Done.", "type": "END" } ] }
                """.formatted(title, author, difficultyField, tags));
    }

    @Test
    void returnsEveryBookWhenNoFiltersAreApplied() {
        assertThat(service.search(null, Set.of(), Set.of())).hasSize(4);
        assertThat(service.search("", null, null)).hasSize(4);
        assertThat(service.search("   ", Set.of(), Set.of())).hasSize(4);
    }

    @Test
    void matchesTitlesRegardlessOfCase() {
        assertThat(service.search("PRISONER", Set.of(), Set.of())).extracting(LoadedBook::title)
                .containsExactly("The Prisoner");
    }

    @Test
    void matchesPartialAuthorNames() {
        assertThat(service.search("stormrider", Set.of(), Set.of())).extracting(LoadedBook::title)
                .containsExactly("The Crystal Caverns");
    }

    @Test
    void returnsNothingWhenTheSearchMatchesNoBook() {
        assertThat(service.search("submarine", Set.of(), Set.of())).isEmpty();
    }

    @Test
    void filtersBySingleDifficulty() {
        assertThat(service.search(null, Set.of(Difficulty.EASY), Set.of())).extracting(LoadedBook::title)
                .containsExactly("The Crystal Caverns");
    }

    @Test
    void filtersByAnyOfSeveralDifficulties() {
        assertThat(service.search(null, Set.of(Difficulty.EASY, Difficulty.HARD), Set.of()))
                .extracting(LoadedBook::slug)
                .containsExactly("crystal-caverns", "dragon-quest", "the-prisoner");
    }

    @Test
    void excludesBooksWithoutADifficultyWhenFiltering() {
        assertThat(service.search(null, Set.of(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD), Set.of()))
                .extracting(LoadedBook::slug)
                .doesNotContain("unlabelled");
    }

    @Test
    void appliesSearchAndDifficultyTogether() {
        assertThat(service.search("the", Set.of(Difficulty.HARD), Set.of())).extracting(LoadedBook::title)
                .containsExactly("The Prisoner");
    }

    @Test
    void filtersByAnySelectedTag() {
        assertThat(service.search(null, Set.of(), Set.of("steampunk", "coastal")))
                .extracting(LoadedBook::slug)
                .containsExactly("crystal-caverns", "the-prisoner");
    }

    @Test
    void combinesTagsWithDifficulty() {
        assertThat(service.search(null, Set.of(Difficulty.HARD), Set.of("Steampunk", "Mystery")))
                .extracting(LoadedBook::slug)
                .containsExactly("dragon-quest", "the-prisoner");
    }

    @Test
    void returnsSortedDistinctTags() {
        assertThat(service.tags()).containsExactly("Coastal", "Mystery", "Steampunk");
    }
}
