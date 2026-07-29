package com.adventurebook.game;

/** Where a play-through stands. Both terminal states are final; a finished game is read-only. */
public enum GameStatus {
    IN_PROGRESS,
    /** An END section was reached. */
    WON,
    /** Health hit zero. */
    DEAD;

    public boolean isFinished() {
        return this != IN_PROGRESS;
    }
}
