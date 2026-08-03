package com.adventurebook.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.adventurebook.save.GameSaveRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SaveAndResumeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GameSaveRepository saves;

    @BeforeEach
    void clearSaves() {
        saves.deleteAll();
    }

    private JsonNode start(String slug, boolean fromSave) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookSlug\":\"%s\",\"fromSave\":%s}".formatted(slug, fromSave)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode choose(String gameId, int index) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/games/%s/choices".formatted(gameId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"optionIndex\":%d}".formatted(index)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private int optionStartingWith(JsonNode state, String prefix) {
        List<JsonNode> options = state.get("section").get("options").valueStream().toList();
        return options.stream()
                .filter(option -> option.get("description").asString().startsWith(prefix))
                .map(option -> option.get("index").asInt())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no option starting with " + prefix));
    }

    @Test
    @DisplayName("a saved game resumes on the same section with the same health")
    void savingThenContinuingRestoresTheExactPosition() throws Exception {
        JsonNode state = start("clockwork-lighthouse", false);
        String gameId = state.get("gameId").asString();
        state = choose(gameId, optionStartingWith(state, "Force the seaward door"));

        String sectionBeforeSaving = state.get("section").get("id").asString();
        assertThat(state.get("health").asInt()).isEqualTo(6);

        mockMvc.perform(post("/api/games/%s/save".formatted(gameId))).andExpect(status().isNoContent());

        JsonNode resumed = start("clockwork-lighthouse", true);

        assertThat(resumed.get("section").get("id").asString()).isEqualTo(sectionBeforeSaving);
        assertThat(resumed.get("health").asInt()).isEqualTo(6);
        assertThat(resumed.get("status").asString()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void resumingIssuesAFreshGameSoTheOldHandleIsNotShared() throws Exception {
        JsonNode state = start("clockwork-lighthouse", false);
        String originalId = state.get("gameId").asString();
        mockMvc.perform(post("/api/games/%s/save".formatted(originalId))).andExpect(status().isNoContent());

        JsonNode resumed = start("clockwork-lighthouse", true);

        assertThat(resumed.get("gameId").asString()).isNotEqualTo(originalId);
        mockMvc.perform(get("/api/games/" + originalId)).andExpect(status().isOk());
    }

    @Test
    void savingTwiceKeepsOnlyTheLatestPosition() throws Exception {
        JsonNode state = start("clockwork-lighthouse", false);
        String gameId = state.get("gameId").asString();
        mockMvc.perform(post("/api/games/%s/save".formatted(gameId))).andExpect(status().isNoContent());

        state = choose(gameId, optionStartingWith(state, "Take the outer stair"));
        String later = state.get("section").get("id").asString();
        mockMvc.perform(post("/api/games/%s/save".formatted(gameId))).andExpect(status().isNoContent());

        assertThat(saves.findAll()).hasSize(1);
        assertThat(start("clockwork-lighthouse", true).get("section").get("id").asString()).isEqualTo(later);
    }

    @Test
    void theLibraryAdvertisesWhichBooksCanBeContinued() throws Exception {
        mockMvc.perform(get("/api/books/clockwork-lighthouse"))
                .andExpect(jsonPath("$.hasSave").value(false));

        String gameId = start("clockwork-lighthouse", false).get("gameId").asString();
        mockMvc.perform(post("/api/games/%s/save".formatted(gameId))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/books/clockwork-lighthouse"))
                .andExpect(jsonPath("$.hasSave").value(true));
        // Books are listed alphabetically by slug, so these positions are stable.
        mockMvc.perform(get("/api/books"))
                .andExpect(jsonPath("$[0].slug").value("clockwork-lighthouse"))
                .andExpect(jsonPath("$[0].hasSave").value(true))
                .andExpect(jsonPath("$[1].slug").value("sunken-orchard"))
                .andExpect(jsonPath("$[1].hasSave").value(false));
    }

    @Test
    void refusesToContinueABookThatWasNeverSaved() throws Exception {
        mockMvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookSlug\":\"sunken-orchard\",\"fromSave\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No saved progress for book 'sunken-orchard'"));
    }

    @Test
    void refusesToSaveAnAdventureThatIsAlreadyOver() throws Exception {
        JsonNode state = start("sunken-orchard", false);
        String gameId = state.get("gameId").asString();
        state = choose(gameId, optionStartingWith(state, "Walk the shoreline"));
        state = choose(gameId, optionStartingWith(state, "Follow the road"));
        state = choose(gameId, optionStartingWith(state, "Take the plate home"));
        assertThat(state.get("status").asString()).isEqualTo("WON");

        mockMvc.perform(post("/api/games/%s/save".formatted(gameId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Adventure already over"));
    }

    @Test
    void reportsSavingAnUnknownGame() throws Exception {
        mockMvc.perform(post("/api/games/1e2d3c4b-0000-0000-0000-000000000000/save"))
                .andExpect(status().isNotFound());
    }

    @Test
    void discardingASaveTakesTheContinueOfferAway() throws Exception {
        String gameId = start("clockwork-lighthouse", false).get("gameId").asString();
        mockMvc.perform(post("/api/games/%s/save".formatted(gameId))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/books/clockwork-lighthouse"))
                .andExpect(jsonPath("$.hasSave").value(true));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/books/clockwork-lighthouse/save"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/books/clockwork-lighthouse"))
                .andExpect(jsonPath("$.hasSave").value(false));
        mockMvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookSlug\":\"clockwork-lighthouse\",\"fromSave\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void discardingABookThatWasNeverSavedIsANoOp() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/books/sunken-orchard/save"))
                .andExpect(status().isNoContent());
    }
}
