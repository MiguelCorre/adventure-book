package com.adventurebook.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.adventurebook.book.Book;
import com.adventurebook.book.BookRepository;
import com.adventurebook.book.BookService;
import com.adventurebook.book.Difficulty;
import com.adventurebook.book.BookUploadService;
import com.adventurebook.book.LoadedBook;
import com.adventurebook.book.ValidationIssue;
import com.adventurebook.book.ValidationReport;
import com.adventurebook.book.ValidationRule;
import com.adventurebook.book.testsupport.Books;
import com.adventurebook.save.SaveService;

@WebMvcTest(BookController.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    @MockitoBean
    private BookRepository bookRepository;

    @MockitoBean
    private SaveService saveService;

    @MockitoBean
    private BookUploadService bookUploadService;

    private static LoadedBook playable(String slug, String title, Difficulty difficulty) {
        Book book = Books.titled(title, difficulty,
                Books.begin("1", Books.goTo("On", "2")), Books.end("2"));
        return new LoadedBook(slug, book, ValidationReport.valid());
    }

    private static LoadedBook trapped(String slug) {
        Book book = Books.titled("Trapped", Difficulty.HARD, Books.begin("1", Books.goTo("On", "2")),
                Books.end("2"), Books.node("666"));
        return new LoadedBook(slug, book, new ValidationReport(List.of(
                ValidationIssue.at(ValidationRule.NO_DEAD_ENDS, "666", "Section 666 traps the player"))));
    }

    @Test
    void listsEveryBookTheServiceReturns() throws Exception {
        given(bookService.search(any(), any(), any())).willReturn(List.of(
                playable("crystal-caverns", "The Crystal Caverns", Difficulty.EASY),
                playable("the-prisoner", "The Prisoner", Difficulty.HARD)));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("crystal-caverns"))
                .andExpect(jsonPath("$[0].title").value("The Crystal Caverns"))
                .andExpect(jsonPath("$[0].difficulty").value("EASY"))
                .andExpect(jsonPath("$[0].sectionCount").value(2))
                .andExpect(jsonPath("$[0].readingMinutes").value(1))
                .andExpect(jsonPath("$[0].valid").value(true))
                .andExpect(jsonPath("$[0].issues").isEmpty());
    }

    @Test
    void exposesOptionalPresentationMetadataWithoutExposingSections() throws Exception {
        Book book = new Book(
                "The Clockwork Lighthouse",
                "Ines Vaz-Corvo",
                "Relight the beacon before a ship reaches the rocks.",
                List.of("Steampunk", "Coastal"),
                Difficulty.MEDIUM,
                List.of(
                        new com.adventurebook.book.Section("1", "The Dark Tower", "Begin.",
                                com.adventurebook.book.SectionType.BEGIN, List.of(Books.goTo("Climb", "2"))),
                        new com.adventurebook.book.Section("2", "The Beacon", "Safe.",
                                com.adventurebook.book.SectionType.END, List.of())));
        given(bookService.search(any(), any(), any()))
                .willReturn(List.of(new LoadedBook("clockwork-lighthouse", book, ValidationReport.valid())));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description")
                        .value("Relight the beacon before a ship reaches the rocks."))
                .andExpect(jsonPath("$[0].tags[0]").value("Steampunk"))
                .andExpect(jsonPath("$[0].tags[1]").value("Coastal"))
                .andExpect(jsonPath("$[0].sections").doesNotExist());
    }

    @Test
    void listsInvalidBooksAlongsideTheirReasons() throws Exception {
        given(bookService.search(any(), any(), any())).willReturn(List.of(trapped("pirates-jade-sea")));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].valid").value(false))
                .andExpect(jsonPath("$[0].issues.length()").value(1))
                .andExpect(jsonPath("$[0].issues[0].rule").value("NO_DEAD_ENDS"))
                .andExpect(jsonPath("$[0].issues[0].sectionId").value("666"))
                .andExpect(jsonPath("$[0].issues[0].message").value("Section 666 traps the player"));
    }

    @Test
    void neverExposesTheStoryItselfInTheCatalogue() throws Exception {
        given(bookService.search(any(), any(), any())).willReturn(List.of(playable("a", "A", Difficulty.EASY)));

        mockMvc.perform(get("/api/books"))
                .andExpect(jsonPath("$[0].sections").doesNotExist())
                .andExpect(jsonPath("$[0].book").doesNotExist());
    }

    @Test
    void passesSearchTextAndDifficultiesThroughToTheService() throws Exception {
        given(bookService.search(any(), any(), any())).willReturn(List.of());

        mockMvc.perform(get("/api/books").param("query", "prisoner").param("difficulty", "EASY", "HARD"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Difficulty>> difficulties = ArgumentCaptor.forClass(Set.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> tags = ArgumentCaptor.forClass(Set.class);
        org.mockito.Mockito.verify(bookService).search(query.capture(), difficulties.capture(), tags.capture());

        org.assertj.core.api.Assertions.assertThat(query.getValue()).isEqualTo("prisoner");
        org.assertj.core.api.Assertions.assertThat(difficulties.getValue())
                .containsExactlyInAnyOrder(Difficulty.EASY, Difficulty.HARD);
        org.assertj.core.api.Assertions.assertThat(tags.getValue()).isNull();
    }

    @Test
    void returnsAnEmptyArrayRatherThanAnErrorForAnEmptyLibrary() throws Exception {
        given(bookService.search(any(), any(), any())).willReturn(List.of());

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void passesTagsToTheServiceAndExposesTagUnion() throws Exception {
        given(bookService.search(any(), any(), any())).willReturn(List.of());
        given(bookService.tags()).willReturn(List.of("Coastal", "Mystery", "Steampunk"));

        mockMvc.perform(get("/api/books").param("tag", "Steampunk", "Mystery"))
                .andExpect(status().isOk());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> tags = ArgumentCaptor.forClass(Set.class);
        org.mockito.Mockito.verify(bookService).search(any(), any(), tags.capture());
        org.assertj.core.api.Assertions.assertThat(tags.getValue()).containsExactlyInAnyOrder("Steampunk", "Mystery");

        mockMvc.perform(get("/api/books/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Coastal"))
                .andExpect(jsonPath("$[2]").value("Steampunk"));
    }

    @Test
    void returnsASingleBookBySlug() throws Exception {
        given(bookRepository.findBySlug(eq("the-prisoner")))
                .willReturn(Optional.of(playable("the-prisoner", "The Prisoner", Difficulty.HARD)));

        mockMvc.perform(get("/api/books/the-prisoner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("the-prisoner"));
    }

    @Test
    void answersWithAProblemDocumentForAnUnknownSlug() throws Exception {
        given(bookRepository.findBySlug(any())).willReturn(Optional.empty());

        mockMvc.perform(get("/api/books/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No book with slug 'nope'"));
    }
}
