package com.adventurebook.save;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Saved progress in one book.
 *
 * <p>The book slug is the primary key, so a book has exactly one slot and saving again
 * overwrites it. That matches how the brief describes the feature — a bookmark, not a
 * history — and keeps the library card down to a single "Continue" action.
 *
 * <p>A class rather than a record because JPA needs a no-argument constructor and
 * mutable fields.
 */
@Entity
@Table(name = "game_save")
public class GameSave {

    @Id
    @Column(name = "book_slug", nullable = false)
    private String bookSlug;

    @Column(name = "section_id", nullable = false)
    private String sectionId;

    @Column(nullable = false)
    private int health;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    protected GameSave() {
        // for JPA
    }

    public GameSave(String bookSlug, String sectionId, int health, Instant savedAt) {
        this.bookSlug = bookSlug;
        this.sectionId = sectionId;
        this.health = health;
        this.savedAt = savedAt;
    }

    public String getBookSlug() {
        return bookSlug;
    }

    public String getSectionId() {
        return sectionId;
    }

    public int getHealth() {
        return health;
    }

    public Instant getSavedAt() {
        return savedAt;
    }

    void update(String sectionId, int health, Instant savedAt) {
        this.sectionId = sectionId;
        this.health = health;
        this.savedAt = savedAt;
    }
}
