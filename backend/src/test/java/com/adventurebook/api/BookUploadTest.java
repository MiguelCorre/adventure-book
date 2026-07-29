package com.adventurebook.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.adventurebook.book.BookRepository;

/**
 * Uploads run against a scratch directory rather than the shipped library, so a test can
 * never leave a stray book behind in {@code books/}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookUploadTest {

    private static final Path UPLOAD_DIR = Path.of("target/upload-test-books");

    private static final String VALID_BOOK = """
            { "title": "The Glass Orchard", "author": "A. Curator", "difficulty": "EASY", "sections": [
              { "id": 1, "text": "You arrive.", "type": "BEGIN",
                "options": [ { "description": "Go in", "gotoId": 2 } ] },
              { "id": 2, "text": "You leave.", "type": "END" } ] }
            """;

    @DynamicPropertySource
    static void useAScratchDirectory(DynamicPropertyRegistry registry) {
        registry.add("adventure.books-dir", UPLOAD_DIR::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookRepository repository;

    @BeforeEach
    void emptyTheDirectory() throws IOException {
        deleteRecursively();
        Files.createDirectories(UPLOAD_DIR);
        repository.reload();
    }

    @AfterEach
    void cleanUp() throws IOException {
        deleteRecursively();
    }

    private void deleteRecursively() throws IOException {
        if (!Files.exists(UPLOAD_DIR)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(UPLOAD_DIR)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }

    private MockMultipartFile upload(String json) {
        return new MockMultipartFile("file", "book.json", "application/json",
                json.getBytes(StandardCharsets.UTF_8));
    }

    private String suppliedBook(String slug) throws IOException {
        return Files.readString(Path.of("src/test/resources/books", slug + ".json"), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a valid book is stored and immediately appears in the library")
    void acceptsAValidBook() throws Exception {
        mockMvc.perform(multipart("/api/books").file(upload(VALID_BOOK)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("the-glass-orchard"))
                .andExpect(jsonPath("$.title").value("The Glass Orchard"))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.sectionCount").value(2));

        assertThat(UPLOAD_DIR.resolve("the-glass-orchard.json")).exists();
        mockMvc.perform(get("/api/books"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("the-glass-orchard"));
    }

    @Test
    @DisplayName("the-prisoner.json is rejected with the rule it breaks")
    void rejectsASuppliedBookAndExplainsWhy() throws Exception {
        mockMvc.perform(multipart("/api/books").file(upload(suppliedBook("the-prisoner"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Book rejected"))
                .andExpect(jsonPath("$.issues.length()").value(1))
                .andExpect(jsonPath("$.issues[0].rule").value("NO_DEAD_ENDS"))
                .andExpect(jsonPath("$.issues[0].sectionId").value("666"));

        assertThat(UPLOAD_DIR.resolve("the-prisoner.json")).doesNotExist();
    }

    @Test
    @DisplayName("a rejected upload reports every problem at once")
    void reportsAllProblemsInOneResponse() throws Exception {
        mockMvc.perform(multipart("/api/books").file(upload(suppliedBook("pirates-jade-sea"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.issues.length()").value(2));
    }

    @Test
    void rejectsAFileThatIsNotJson() throws Exception {
        mockMvc.perform(multipart("/api/books").file(upload("this is not a book")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.issues[0].rule").value("UNREADABLE"));
    }

    @Test
    void rejectsAnEmptyFile() throws Exception {
        mockMvc.perform(multipart("/api/books").file(upload("")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.issues[0].rule").value("UNREADABLE"));
    }

    @Test
    void rejectsASecondBookWithTheSameTitle() throws Exception {
        mockMvc.perform(multipart("/api/books").file(upload(VALID_BOOK))).andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/books").file(upload(VALID_BOOK)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Book already exists"));
    }

    @Test
    @DisplayName("the stored filename comes from the title, never from the uploaded filename")
    void ignoresTheClientSuppliedFilename() throws Exception {
        MockMultipartFile hostile = new MockMultipartFile("file", "../../escaped.json", "application/json",
                VALID_BOOK.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/books").file(hostile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("the-glass-orchard"));

        assertThat(UPLOAD_DIR.resolve("the-glass-orchard.json")).exists();
        assertThat(UPLOAD_DIR.getParent().resolve("escaped.json")).doesNotExist();
    }

    @Test
    void anUploadedBookCanBePlayedStraightAway() throws Exception {
        mockMvc.perform(multipart("/api/books").file(upload(VALID_BOOK))).andExpect(status().isCreated());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/games")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"bookSlug\":\"the-glass-orchard\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.section.text").value("You arrive."));
    }
}
