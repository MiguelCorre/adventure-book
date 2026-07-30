package com.adventurebook.game;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Component;

import com.adventurebook.game.GameExceptions.GameNotFoundException;

/**
 * Holds the games currently being played.
 *
 * <p>Deliberately in memory. A session is a transient thing — a browser tab someone has
 * open — and losing it on restart costs the player nothing they cannot rebuild, because
 * durable progress is what "save" is for. Anything worth keeping goes to the database.
 */
@Component
public class SessionRegistry {

    private final Map<UUID, GameSession> sessions = new ConcurrentHashMap<>();

    public GameSession put(GameSession session) {
        sessions.put(session.id(), session);
        return session;
    }

    /**
     * Applies one move to a stored session atomically.
     *
     * <p>{@link ConcurrentHashMap} runs the remapping function under the key's lock, so
     * two simultaneous choices for the same game serialise instead of both starting from
     * the same section and silently overwriting each other. The second move sees the state
     * the first produced — and if the first finished the adventure, the second fails the
     * way any late choice does, rather than resurrecting the game.
     *
     * <p>A move that throws leaves the stored session untouched.
     */
    public GameSession update(UUID id, UnaryOperator<GameSession> move) {
        GameSession moved = sessions.computeIfPresent(id, (key, session) -> move.apply(session));
        if (moved == null) {
            throw new GameNotFoundException(String.valueOf(id));
        }
        return moved;
    }

    public Optional<GameSession> find(UUID id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public void remove(UUID id) {
        sessions.remove(id);
    }

    public int size() {
        return sessions.size();
    }
}
