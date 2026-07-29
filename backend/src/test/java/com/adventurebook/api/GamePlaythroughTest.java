package com.adventurebook.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Plays the shipped books through the real HTTP API.
 *
 * <p>Runs against {@code ../books} rather than a fixture directory, so these tests fail
 * if the content that ships with the application stops supporting the mechanics the brief
 * asks us to demonstrate.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GamePlaythroughTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode startGame(String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookSlug\":\"%s\"}".formatted(slug)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode choose(String gameId, int optionIndex) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/games/%s/choices".formatted(gameId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"optionIndex\":%d}".formatted(optionIndex)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Index of the option whose text starts with the given words, in the current section. */
    private int optionStartingWith(JsonNode state, String prefix) {
        List<JsonNode> options = state.get("section").get("options").valueStream().toList();
        for (JsonNode option : options) {
            if (option.get("description").asString().startsWith(prefix)) {
                return option.get("index").asInt();
            }
        }
        throw new AssertionError("no option starting with \"" + prefix + "\" in section "
                + state.get("section").get("id").asString());
    }

    @Test
    @DisplayName("a game starts at the beginning with a full health bar")
    void startingAGameOpensTheFirstSection() throws Exception {
        JsonNode state = startGame("clockwork-lighthouse");

        assertThat(state.get("bookTitle").asString()).isEqualTo("The Clockwork Lighthouse");
        assertThat(state.get("health").asInt()).isEqualTo(10);
        assertThat(state.get("maxHealth").asInt()).isEqualTo(10);
        assertThat(state.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(state.get("section").get("type").asString()).isEqualTo("BEGIN");
        assertThat(state.get("section").get("options").size()).isEqualTo(2);
        assertThat(state.get("lastConsequence").isNull()).isTrue();
    }

    @Test
    @DisplayName("the state never reveals where a choice leads or what it costs")
    void optionsCarryNothingButAnIndexAndTheirWords() throws Exception {
        JsonNode state = startGame("clockwork-lighthouse");

        JsonNode firstOption = state.get("section").get("options").get(0);
        assertThat(firstOption.propertyNames()).containsExactlyInAnyOrder("index", "description");
        assertThat(state.toString()).doesNotContain("gotoId");
    }

    @Test
    @DisplayName("the safe route reaches an ending without losing any health")
    void playingCarefullyWinsTheAdventure() throws Exception {
        JsonNode state = startGame("clockwork-lighthouse");
        String gameId = state.get("gameId").asString();

        state = choose(gameId, optionStartingWith(state, "Take the outer stair"));
        state = choose(gameId, optionStartingWith(state, "Keep climbing"));
        state = choose(gameId, optionStartingWith(state, "Duck through"));
        state = choose(gameId, optionStartingWith(state, "Fetch the crank"));

        assertThat(state.get("status").asString()).isEqualTo("WON");
        assertThat(state.get("health").asInt()).isEqualTo(10);
        assertThat(state.get("section").get("type").asString()).isEqualTo("END");
        assertThat(state.get("section").get("text").asString()).contains("lens begins to turn");
    }

    @Test
    @DisplayName("the reckless route kills the player before the third choice resolves")
    void playingRecklesslyEndsInDeath() throws Exception {
        JsonNode state = startGame("clockwork-lighthouse");
        String gameId = state.get("gameId").asString();

        state = choose(gameId, optionStartingWith(state, "Force the seaward door"));
        assertThat(state.get("health").asInt()).isEqualTo(6);
        assertThat(state.get("lastConsequence").get("type").asString()).isEqualTo("LOSE_HEALTH");

        state = choose(gameId, optionStartingWith(state, "Wade straight through"));
        assertThat(state.get("health").asInt()).isEqualTo(2);

        state = choose(gameId, optionStartingWith(state, "Pry the jammed gear"));

        assertThat(state.get("status").asString()).isEqualTo("DEAD");
        assertThat(state.get("health").asInt()).isZero();
        assertThat(state.get("lastConsequence").get("text").asString()).contains("fingers still inside it");
    }

    @Test
    @DisplayName("healing stops at the starting health bar")
    void healingCannotPushThePlayerAboveFullHealth() throws Exception {
        JsonNode state = startGame("clockwork-lighthouse");
        String gameId = state.get("gameId").asString();

        state = choose(gameId, optionStartingWith(state, "Force the seaward door"));
        assertThat(state.get("health").asInt()).isEqualTo(6);

        state = choose(gameId, optionStartingWith(state, "Edge along the wall"));
        state = choose(gameId, optionStartingWith(state, "Open the tin"));

        assertThat(state.get("lastConsequence").get("value").asInt()).isEqualTo(6);
        assertThat(state.get("health").asInt()).as("6 + 6 capped at 10").isEqualTo(10);
    }

    @Test
    void refusesAnyFurtherChoiceOnceTheAdventureIsOver() throws Exception {
        JsonNode state = startGame("sunken-orchard");
        String gameId = state.get("gameId").asString();

        state = choose(gameId, optionStartingWith(state, "Walk the shoreline"));
        state = choose(gameId, optionStartingWith(state, "Follow the road"));
        state = choose(gameId, optionStartingWith(state, "Take the plate home"));
        assertThat(state.get("status").asString()).isEqualTo("WON");

        mockMvc.perform(post("/api/games/%s/choices".formatted(gameId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"optionIndex\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Adventure already over"));
    }

    @Test
    void refusesToStartABookThatFailedValidation() throws Exception {
        mockMvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookSlug\":\"pirates-jade-sea\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Book cannot be played"));
    }

    @Test
    void reportsAnUnknownBook() throws Exception {
        mockMvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookSlug\":\"no-such-book\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reportsAnUnknownGame() throws Exception {
        mockMvc.perform(get("/api/games/1e2d3c4b-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAChoiceTheCurrentSectionDoesNotOffer() throws Exception {
        String gameId = startGame("sunken-orchard").get("gameId").asString();

        mockMvc.perform(post("/api/games/%s/choices".formatted(gameId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"optionIndex\":99}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Choice not available"));
    }

    @Test
    void returnsTheCurrentStateOfAGameInProgress() throws Exception {
        JsonNode started = startGame("sunken-orchard");
        String gameId = started.get("gameId").asString();

        mockMvc.perform(get("/api/games/" + gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andExpect(jsonPath("$.bookSlug").value("sunken-orchard"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }
}
