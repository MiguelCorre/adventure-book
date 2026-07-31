package com.adventurebook.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A caller's mistake must never be reported as a server fault.
 *
 * <p>These all answered 500 before {@code ApiExceptionHandler} started extending Spring's
 * {@code ResponseEntityExceptionHandler}: the catch-all was intercepting the framework's
 * own well-typed failures and blaming us for them.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MalformedRequestTest {

    @Autowired
    private MockMvc mockMvc;

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
            String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    @DisplayName("a path variable that is not a UUID is a bad request")
    void rejectsAnUnparseableGameId() throws Exception {
        mockMvc.perform(get("/api/games/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsABodyThatIsNotJson() throws Exception {
        mockMvc.perform(json("/api/games", "{\"bookSlug\":")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnEmptyBody() throws Exception {
        mockMvc.perform(json("/api/games", "")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAStartRequestWithoutABookSlug() throws Exception {
        mockMvc.perform(json("/api/games", "{}")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAStartRequestWithABlankBookSlug() throws Exception {
        mockMvc.perform(json("/api/games", "{\"bookSlug\":\"   \"}")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsANegativeChoiceIndex() throws Exception {
        mockMvc.perform(json("/api/games/1e2d3c4b-0000-0000-0000-000000000000/choices",
                "{\"optionIndex\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAChoiceWithoutAnIndex() throws Exception {
        mockMvc.perform(json("/api/games/1e2d3c4b-0000-0000-0000-000000000000/choices", "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsANullChoiceIndex() throws Exception {
        mockMvc.perform(json("/api/games/1e2d3c4b-0000-0000-0000-000000000000/choices",
                "{\"optionIndex\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a difficulty outside the enum is a bad request, not a server error")
    void rejectsAnUnknownDifficulty() throws Exception {
        mockMvc.perform(get("/api/books").param("difficulty", "SOMEWHAT_TRICKY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnUploadWithNoFilePart() throws Exception {
        mockMvc.perform(multipart("/api/books")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnUnsupportedMethodOnAKnownPath() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/books"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("domain failures keep their own statuses")
    void stillReportsDomainFailuresCorrectly() throws Exception {
        mockMvc.perform(get("/api/books/no-such-book")).andExpect(status().isNotFound());
        mockMvc.perform(json("/api/games", "{\"bookSlug\":\"pirates-jade-sea\"}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/games/1e2d3c4b-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
