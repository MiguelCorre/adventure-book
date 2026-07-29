package com.adventurebook.book;

/**
 * Health effect applied when a player takes the owning option.
 *
 * <p>The sample books encode {@code value} as a JSON string ({@code "value": "6"});
 * Jackson coerces it to {@code int}. A value that is not a whole number makes the
 * book unreadable, which surfaces as a parse error rather than a crash.
 *
 * @param type  whether health is lost or gained
 * @param value magnitude of the effect, always positive
 * @param text  narration shown to the player when the effect is applied
 */
public record Consequence(ConsequenceType type, int value, String text) {

    public Consequence {
        if (value < 0) {
            throw new IllegalArgumentException("consequence value must not be negative: " + value);
        }
    }
}
