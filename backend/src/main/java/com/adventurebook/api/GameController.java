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
import com.adventurebook.save.GameSave;
import com.adventurebook.save.NoSavedProgressException;
import com.adventurebook.save.SaveService;

import jakarta.validation.Valid;

/** Play-through lifecycle: open a book, make choices, read the current state. */
@RestController
@RequestMapping(path = "/api/games", produces = MediaType.APPLICATION_JSON_VALUE)
public class GameController {

    private final GameService games;
    private final SaveService saves;

    public GameController(GameService games, SaveService saves) {
        this.games = games;
        this.saves = saves;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameState start(@Valid @RequestBody StartGameRequest request) {
        String slug = request.bookSlug();
        if (!request.fromSave()) {
            return view(games.start(slug));
        }
        GameSave save = saves.find(slug).orElseThrow(() -> new NoSavedProgressException(slug));
        return view(games.resume(slug, save.getSectionId(), save.getHealth()));
    }

    /** Stores the current position so the player can pick the book up again later. */
    @PostMapping("/{gameId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@PathVariable UUID gameId) {
        games.withSession(gameId, saves::save);
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
