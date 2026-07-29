package com.adventurebook.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BookRepositoryTest {

    private static final String PLAYABLE = """
            { "title": "Playable", "author": "A. Writer", "difficulty": "EASY", "sections": [
              { "id": 1, "text": "Start.", "type": "BEGIN",
                "options": [ { "description": "Finish", "gotoId": 2 } ] },
              { "id": 2, "text": "Done.", "type": "END" } ] }
            """;

    private BookRepository repositoryOn(Path directory) {
        BookRepository repository = new BookRepository(directory, new BookJsonMapper(), new ValidationEngine());
        repository.reload();
        return repository;
    }

    private void write(Path directory, String filename, String content) throws IOException {
        Files.writeString(directory.resolve(filename), content, StandardCharsets.UTF_8);
    }

    @Test
    void derivesTheSlugFromTheFilename(@TempDir Path books) throws IOException {
        write(books, "the-lost-city.json", PLAYABLE);

        assertThat(repositoryOn(books).findBySlug("the-lost-city")).isPresent();
    }

    @Test
    void listsBooksInAStableAlphabeticalOrder(@TempDir Path books) throws IOException {
        write(books, "zeta.json", PLAYABLE);
        write(books, "alpha.json", PLAYABLE);
        write(books, "mid.json", PLAYABLE);

        assertThat(repositoryOn(books).findAll()).extracting(LoadedBook::slug)
                .containsExactly("alpha", "mid", "zeta");
    }

    @Test
    void keepsLoadingAfterAFileFailsToParse(@TempDir Path books) throws IOException {
        write(books, "good.json", PLAYABLE);
        write(books, "broken.json", "{ not json at all");

        var repository = repositoryOn(books);

        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.findBySlug("good").orElseThrow().isPlayable()).isTrue();
        assertThat(repository.findBySlug("broken").orElseThrow().report().issues())
                .singleElement()
                .satisfies(issue -> assertThat(issue.rule()).isEqualTo(ValidationRule.UNREADABLE));
    }

    @Test
    void ignoresFilesThatAreNotJson(@TempDir Path books) throws IOException {
        write(books, "book.json", PLAYABLE);
        write(books, "notes.txt", "not a book");
        write(books, "README", "not a book either");

        assertThat(repositoryOn(books).findAll()).extracting(LoadedBook::slug).containsExactly("book");
    }

    @Test
    void reportsAnEmptyLibraryWhenTheDirectoryIsMissing(@TempDir Path parent) {
        var repository = repositoryOn(parent.resolve("does-not-exist"));

        assertThat(repository.findAll()).isEmpty();
        assertThat(repository.findBySlug("anything")).isEmpty();
    }

    @Test
    void reportsAnEmptyLibraryWhenTheDirectoryHasNoBooks(@TempDir Path books) {
        assertThat(repositoryOn(books).findAll()).isEmpty();
    }

    @Test
    void picksUpFilesAddedAfterStartupOnReload(@TempDir Path books) throws IOException {
        write(books, "first.json", PLAYABLE);
        var repository = repositoryOn(books);
        assertThat(repository.findAll()).hasSize(1);

        write(books, "second.json", PLAYABLE);
        repository.reload();

        assertThat(repository.findAll()).extracting(LoadedBook::slug).containsExactly("first", "second");
        assertThat(repository.exists("second")).isTrue();
    }
}
