package com.adventurebook.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import com.adventurebook.game.GameExceptions.GameNotFoundException;

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
    void evictsTheOldestSessionWhenCapacityIsReached() {
        SessionRegistry bounded = new SessionRegistry(2);
        UUID oldest = UUID.randomUUID();
        UUID middle = UUID.randomUUID();
        UUID newest = UUID.randomUUID();

        bounded.put(session(oldest, "1", 10));
        bounded.put(session(middle, "2", 8));
        bounded.put(session(newest, "3", 6));

        assertThat(bounded.find(oldest)).isEmpty();
        assertThat(bounded.find(middle)).isPresent();
        assertThat(bounded.find(newest)).isPresent();
        assertThat(bounded.size()).isEqualTo(2);
    }

    @Test
    void forgetsARemovedSession() {
        UUID id = UUID.randomUUID();
        registry.put(session(id, "1", 10));

        registry.remove(id);

        assertThat(registry.find(id)).isEmpty();
        assertThat(registry.size()).isZero();
    }

    @Test
    void appliesAMoveAndStoresItsResult() {
        UUID id = UUID.randomUUID();
        registry.put(session(id, "1", 10));

        GameSession moved = registry.update(id,
                current -> new GameSession(current.id(), current.bookSlug(), "2", 6,
                        GameStatus.IN_PROGRESS, null));

        assertThat(moved.sectionId()).isEqualTo("2");
        assertThat(registry.find(id).orElseThrow().health()).isEqualTo(6);
    }

    @Test
    void refusesAMoveForAnUnknownGame() {
        assertThatThrownBy(() -> registry.update(UUID.randomUUID(), current -> current))
                .isInstanceOf(GameNotFoundException.class);
    }

    @Test
    void aMoveThatThrowsLeavesTheStoredSessionUntouched() {
        UUID id = UUID.randomUUID();
        registry.put(session(id, "1", 10));

        assertThatThrownBy(() -> registry.update(id, current -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(registry.find(id).orElseThrow().sectionId()).isEqualTo("1");
    }

    @Test
    void aMoveWaitsUntilAnAtomicSnapshotActionHasFinished() throws Exception {
        UUID id = UUID.randomUUID();
        registry.put(session(id, "1", 10));
        CountDownLatch snapshotEntered = new CountDownLatch(1);
        CountDownLatch releaseSnapshot = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var snapshot = executor.submit(() -> registry.withSession(id, current -> {
                snapshotEntered.countDown();
                try {
                    releaseSnapshot.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertThat(snapshotEntered.await(1, TimeUnit.SECONDS)).isTrue();

            var move = executor.submit(() -> registry.update(id,
                    current -> session(id, "2", current.health())));

            try {
                assertThatThrownBy(() -> move.get(100, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                releaseSnapshot.countDown();
            }

            snapshot.get(1, TimeUnit.SECONDS);
            assertThat(move.get(1, TimeUnit.SECONDS).sectionId()).isEqualTo("2");
        }
    }
}
