package com.adventurebook.game;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

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
