package com.adventurebook.book;

/**
 * Canonical form of a section identifier.
 *
 * <p>Book files are inconsistent: the same section may be declared as {@code "id": "500"}
 * and referenced as {@code "gotoId": 500}. Jackson maps both onto {@code String}, so the
 * only remaining difference is incidental whitespace. Normalising in one place keeps the
 * definition of "same section" from drifting between the loader, the validator and the
 * game engine.
 */
final class SectionId {

    private SectionId() {
    }

    static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
