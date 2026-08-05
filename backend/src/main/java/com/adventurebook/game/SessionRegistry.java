package com.adventurebook.game;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.adventurebook.game.GameExceptions.GameNotFoundException;

/**
 * Holds the games currently being played.
 *
 * <p>Deliberately in memory. A session is a transient thing — a browser tab someone has
 * open — and losing it on restart costs the player nothing they cannot rebuild, because
 * durable progress is what "save" is for. Anything worth keeping goes to the database.
 *
 * <p>The registry is bounded. When it reaches capacity, creating a game evicts the
 * oldest session so abandoned browser tabs cannot grow memory use without limit.
 */
@Component
public class SessionRegistry {

    private static final int DEFAULT_CAPACITY = 1_000;

    private final Map<UUID, GameSession> sessions = new ConcurrentHashMap<>();
    private final Queue<UUID> insertionOrder = new ArrayDeque<>();
    private final int capacity;

    @Autowired
    public SessionRegistry() {
        this(DEFAULT_CAPACITY);
    }

    SessionRegistry(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Session capacity must be greater than zero");
        }
        this.capacity = capacity;
    }

    public synchronized GameSession put(GameSession session) {
        GameSession previous = sessions.put(session.id(), session);
        if (previous == null) {
            insertionOrder.add(session.id());
            evictExcess();
        }
        return session;
    }

    private void evictExcess() {
        while (sessions.size() > capacity) {
            sessions.remove(insertionOrder.remove());
        }
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

    /** Runs an action against one immutable snapshot without holding a map lock. */
    public GameSession withSession(UUID id, Consumer<GameSession> action) {
        GameSession snapshot = sessions.get(id);
        if (snapshot == null) {
            throw new GameNotFoundException(String.valueOf(id));
        }
        action.accept(snapshot);
        return snapshot;
    }

    public Optional<GameSession> find(UUID id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public synchronized void remove(UUID id) {
        if (sessions.remove(id) != null) {
            insertionOrder.remove(id);
        }
    }

    public int size() {
        return sessions.size();
    }
}
