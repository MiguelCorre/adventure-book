package com.adventurebook.game;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.adventurebook.book.BookNotFoundException;
import com.adventurebook.book.BookRepository;
import com.adventurebook.book.LoadedBook;
import com.adventurebook.game.GameExceptions.GameNotFoundException;

/**
 * Ties a game handle to the book it is being played from.
 *
 * <p>{@link GameEngine} knows the rules but holds nothing; {@link SessionRegistry} holds
 * state but knows no rules. This is where the two meet, so both stay easy to test on
 * their own.
 */
@Service
public class GameService {

    private final BookRepository books;
    private final GameEngine engine;
    private final SessionRegistry sessions;

    public GameService(BookRepository books, GameEngine engine, SessionRegistry sessions) {
        this.books = books;
        this.engine = engine;
        this.sessions = sessions;
    }

    public GameSession start(String bookSlug) {
        return sessions.put(engine.start(bookOf(bookSlug)));
    }

    public GameSession resume(String bookSlug, String sectionId, int health) {
        return sessions.put(engine.resume(bookOf(bookSlug), sectionId, health));
    }

    public GameSession choose(UUID gameId, int optionIndex) {
        GameSession session = require(gameId);
        return sessions.put(engine.choose(bookOf(session.bookSlug()), session, optionIndex));
    }

    public GameSession require(UUID gameId) {
        return sessions.find(gameId).orElseThrow(() -> new GameNotFoundException(String.valueOf(gameId)));
    }

    public LoadedBook bookOf(String slug) {
        return books.findBySlug(slug).orElseThrow(() -> new BookNotFoundException(slug));
    }

    public int startingHealth() {
        return engine.startingHealth();
    }
}
