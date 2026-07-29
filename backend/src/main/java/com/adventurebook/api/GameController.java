package com.adventurebook.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.adventurebook.api.dto.ChoiceRequest;
import com.adventurebook.api.dto.GameState;
import com.adventurebook.api.dto.StartGameRequest;
import com.adventurebook.book.LoadedBook;
import com.adventurebook.book.Section;
import com.adventurebook.game.GameExceptions.SectionNotFoundException;
import com.adventurebook.game.GameService;
import com.adventurebook.game.GameSession;

import jakarta.validation.Valid;

/** Play-through lifecycle: open a book, make choices, read the current state. */
@RestController
@RequestMapping(path = "/api/games", produces = MediaType.APPLICATION_JSON_VALUE)
public class GameController {

    private final GameService games;

    public GameController(GameService games) {
        this.games = games;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameState start(@Valid @RequestBody StartGameRequest request) {
        return view(games.start(request.bookSlug()));
    }

    @PostMapping("/{gameId}/choices")
    public GameState choose(@PathVariable UUID gameId, @Valid @RequestBody ChoiceRequest request) {
        return view(games.choose(gameId, request.optionIndex()));
    }

    @GetMapping("/{gameId}")
    public GameState get(@PathVariable UUID gameId) {
        return view(games.require(gameId));
    }

    private GameState view(GameSession session) {
        LoadedBook book = games.bookOf(session.bookSlug());
        Section section = book.content()
                .flatMap(content -> content.section(session.sectionId()))
                .orElseThrow(() -> new SectionNotFoundException(session.sectionId(), session.bookSlug()));
        return GameState.of(session, book, section, games.startingHealth());
    }
}
