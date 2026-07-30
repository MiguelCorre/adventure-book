package com.adventurebook.save;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.adventurebook.game.GameExceptions.GameFinishedException;
import com.adventurebook.game.GameSession;
import com.adventurebook.game.GameStatus;

@DataJpaTest
class SaveServiceTest {

    private static final Instant FIRST = Instant.parse("2026-07-29T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-07-29T11:30:00Z");

    @Autowired
    private GameSaveRepository repository;

    private SaveService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = FIRST;
        service = new SaveService(repository, Clock.fixed(FIRST, ZoneOffset.UTC));
    }

    private SaveService serviceAt(Instant instant) {
        return new SaveService(repository, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private GameSession session(String slug, String sectionId, int health, GameStatus status) {
        return new GameSession(UUID.randomUUID(), slug, sectionId, health, status, null);
    }

    @Test
    void storesProgressForABookThatHasNeverBeenSaved() {
        service.save(session("lighthouse", "40", 6, GameStatus.IN_PROGRESS));

        assertThat(service.find("lighthouse")).hasValueSatisfying(save -> {
            assertThat(save.getSectionId()).isEqualTo("40");
            assertThat(save.getHealth()).isEqualTo(6);
            assertThat(save.getSavedAt()).isEqualTo(now);
        });
    }

    @Test
    void keepsOnlyOneSlotPerBookAndOverwritesIt() {
        service.save(session("lighthouse", "10", 10, GameStatus.IN_PROGRESS));

        serviceAt(LATER).save(session("lighthouse", "70", 3, GameStatus.IN_PROGRESS));

        assertThat(repository.findAll()).hasSize(1);
        assertThat(service.find("lighthouse")).hasValueSatisfying(save -> {
            assertThat(save.getSectionId()).isEqualTo("70");
            assertThat(save.getHealth()).isEqualTo(3);
            assertThat(save.getSavedAt()).isEqualTo(LATER);
        });
    }

    @Test
    void keepsBooksApart() {
        service.save(session("lighthouse", "40", 6, GameStatus.IN_PROGRESS));
        service.save(session("orchard", "20", 9, GameStatus.IN_PROGRESS));

        assertThat(service.find("lighthouse").orElseThrow().getSectionId()).isEqualTo("40");
        assertThat(service.find("orchard").orElseThrow().getSectionId()).isEqualTo("20");
        assertThat(service.slugsWithSavedProgress()).containsExactlyInAnyOrder("lighthouse", "orchard");
    }

    @Test
    void reportsNoProgressForAnUnsavedBook() {
        assertThat(service.find("never-played")).isEmpty();
        assertThat(service.exists("never-played")).isFalse();
        assertThat(service.slugsWithSavedProgress()).isEmpty();
    }

    @Test
    void refusesToSaveAWonAdventure() {
        assertThatThrownBy(() -> service.save(session("lighthouse", "80", 7, GameStatus.WON)))
                .isInstanceOf(GameFinishedException.class);

        assertThat(service.exists("lighthouse")).isFalse();
    }

    @Test
    void refusesToSaveADeadPlayer() {
        assertThatThrownBy(() -> service.save(session("lighthouse", "40", 0, GameStatus.DEAD)))
                .isInstanceOf(GameFinishedException.class);
    }

    @Test
    void forgetsADiscardedSave() {
        service.save(session("lighthouse", "40", 6, GameStatus.IN_PROGRESS));

        service.discard("lighthouse");

        assertThat(service.exists("lighthouse")).isFalse();
    }

    @Test
    void discardingABookThatWasNeverSavedIsANoOp() {
        assertThatCode(() -> service.discard("never-played")).doesNotThrowAnyException();
    }
}
