package com.adventurebook.save;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameSaveRepository extends JpaRepository<GameSave, String> {

    /** H2's single-statement upsert avoids a check-then-insert race on a new book slot. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            MERGE INTO game_save (book_slug, section_id, health, saved_at)
            KEY (book_slug)
            VALUES (:bookSlug, :sectionId, :health, :savedAt)
            """, nativeQuery = true)
    void upsert(@Param("bookSlug") String bookSlug,
            @Param("sectionId") String sectionId,
            @Param("health") int health,
            @Param("savedAt") Instant savedAt);

    @Override
    List<GameSave> findAll();
}
