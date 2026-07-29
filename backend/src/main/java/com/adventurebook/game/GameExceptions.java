package com.adventurebook.game;

/** Failures the game engine reports to callers, each mapping onto a distinct HTTP status. */
public final class GameExceptions {

    private GameExceptions() {
    }

    /** The book exists but its structure makes it impossible to play. */
    public static class BookNotPlayableException extends RuntimeException {
        public BookNotPlayableException(String slug) {
            super("Book '%s' has validation errors and cannot be played".formatted(slug));
        }
    }

    /** A choice arrived for a game that has already been won or lost. */
    public static class GameFinishedException extends RuntimeException {
        public GameFinishedException(GameStatus status) {
            super("This adventure is already over (%s)".formatted(status));
        }
    }

    /** The chosen option does not exist in the current section. */
    public static class InvalidChoiceException extends RuntimeException {
        public InvalidChoiceException(int index, int available) {
            super("Choice %d is not available; this section offers %d".formatted(index, available));
        }
    }

    /** The session points at a section the book does not contain. */
    public static class SectionNotFoundException extends RuntimeException {
        public SectionNotFoundException(String sectionId, String slug) {
            super("Section '%s' does not exist in book '%s'".formatted(sectionId, slug));
        }
    }

    /** No game is registered under the given handle. */
    public static class GameNotFoundException extends RuntimeException {
        public GameNotFoundException(String id) {
            super("No game with id '%s'".formatted(id));
        }
    }
}
