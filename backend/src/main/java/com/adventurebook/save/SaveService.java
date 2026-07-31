package com.adventurebook.save;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adventurebook.game.GameExceptions.GameFinishedException;
import com.adventurebook.game.GameSession;

/**
 * Durable progress, one slot per book.
 *
 * <p>Sessions live in memory and disappear with the process; this is the part the player
 * expects to survive a restart, which is why it goes to the database.
 */
@Service
public class SaveService {

    private final GameSaveRepository saves;
    private final Clock clock;

    @Autowired
    public SaveService(GameSaveRepository saves) {
        this(saves, Clock.systemUTC());
    }

    /** Test seam: pin the clock so saved timestamps are assertable. */
    SaveService(GameSaveRepository saves, Clock clock) {
        this.saves = saves;
        this.clock = clock;
    }

    /**
     * Writes the session into the book's slot, replacing whatever was there.
     *
     * <p>Refuses a finished adventure: resuming it would only put the player back on a
     * screen with nothing left to do.
     */
    @Transactional
    public GameSave save(GameSession session) {
        if (session.status().isFinished()) {
            throw new GameFinishedException(session.status());
        }

        Instant savedAt = clock.instant();
        saves.upsert(session.bookSlug(), session.sectionId(), session.health(), savedAt);
        return saves.findById(session.bookSlug()).orElseThrow(() -> new IllegalStateException(
                "Save for book '%s' disappeared after it was written".formatted(session.bookSlug())));
    }

    @Transactional(readOnly = true)
    public Optional<GameSave> find(String bookSlug) {
        return saves.findById(bookSlug);
    }

    @Transactional(readOnly = true)
    public boolean exists(String bookSlug) {
        return saves.existsById(bookSlug);
    }

    /** One query for the whole library, so listing books does not fan out per card. */
    @Transactional(readOnly = true)
    public Set<String> slugsWithSavedProgress() {
        return saves.findAll().stream().map(GameSave::getBookSlug).collect(Collectors.toSet());
    }

    @Transactional
    public void discard(String bookSlug) {
        saves.deleteById(bookSlug);
    }
}
