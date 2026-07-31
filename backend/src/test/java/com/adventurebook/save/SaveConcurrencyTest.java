package com.adventurebook.save;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.adventurebook.game.GameSession;
import com.adventurebook.game.GameStatus;

@DataJpaTest
@Import(SaveService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SaveConcurrencyTest {

    private static final String BOOK = "shared-book";

    @Autowired
    private SaveService service;

    @Autowired
    private GameSaveRepository repository;

    @AfterEach
    void cleanUp() {
        service.discard(BOOK);
    }

    @Test
    void twoFirstSavesForTheSameBookBothCompleteAndLeaveOneWholeSlot() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> saveWhenReleased(
                    session("10", 10), ready, start));
            var second = executor.submit(() -> saveWhenReleased(
                    session("20", 6), ready, start));

            assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        }

        assertThat(repository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getBookSlug()).isEqualTo(BOOK);
            assertThat(saved.getSectionId()).isIn("10", "20");
            if (saved.getSectionId().equals("10")) {
                assertThat(saved.getHealth()).isEqualTo(10);
            } else {
                assertThat(saved.getHealth()).isEqualTo(6);
            }
        });
    }

    private GameSave saveWhenReleased(GameSession session, CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertThat(start.await(1, TimeUnit.SECONDS)).isTrue();
        return service.save(session);
    }

    private GameSession session(String sectionId, int health) {
        return new GameSession(UUID.randomUUID(), BOOK, sectionId, health,
                GameStatus.IN_PROGRESS, null);
    }
}
