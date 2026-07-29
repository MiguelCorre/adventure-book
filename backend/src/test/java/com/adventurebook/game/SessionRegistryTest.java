package com.adventurebook.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class SessionRegistryTest {

    private final SessionRegistry registry = new SessionRegistry();

    private GameSession session(UUID id, String sectionId, int health) {
        return new GameSession(id, "book", sectionId, health, GameStatus.IN_PROGRESS, null);
    }

    @Test
    void storesAndReturnsASessionByItsHandle() {
        UUID id = UUID.randomUUID();

        registry.put(session(id, "1", 10));

        assertThat(registry.find(id)).hasValueSatisfying(found -> {
            assertThat(found.sectionId()).isEqualTo("1");
            assertThat(found.health()).isEqualTo(10);
        });
    }

    @Test
    void returnsEmptyForAnUnknownHandle() {
        assertThat(registry.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void replacesTheStoredStateWhenTheGameMovesOn() {
        UUID id = UUID.randomUUID();
        registry.put(session(id, "1", 10));

        registry.put(session(id, "2", 6));

        assertThat(registry.find(id)).hasValueSatisfying(found -> {
            assertThat(found.sectionId()).isEqualTo("2");
            assertThat(found.health()).isEqualTo(6);
        });
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void keepsGamesApart() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        registry.put(session(first, "1", 10));
        registry.put(session(second, "5", 3));

        assertThat(registry.find(first).orElseThrow().sectionId()).isEqualTo("1");
        assertThat(registry.find(second).orElseThrow().sectionId()).isEqualTo("5");
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void forgetsARemovedSession() {
        UUID id = UUID.randomUUID();
        registry.put(session(id, "1", 10));

        registry.remove(id);

        assertThat(registry.find(id)).isEmpty();
        assertThat(registry.size()).isZero();
    }
}
