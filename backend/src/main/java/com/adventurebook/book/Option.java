package com.adventurebook.book;

import java.util.Optional;

/**
 * A choice offered at the end of a section.
 *
 * <p>{@code gotoId} is modelled as a {@code String} because the sample books mix
 * numeric and quoted identifiers for the very same section ({@code "gotoId": 500}
 * pointing at {@code "id": "500"}). Normalising both sides to a trimmed string makes
 * the two forms equal instead of producing a phantom broken reference.
 *
 * @param description text shown on the choice button
 * @param gotoId      identifier of the section this choice leads to
 * @param consequence health effect applied when taking this choice, may be {@code null}
 */
public record Option(String description, String gotoId, Consequence consequence) {

    public Option {
        gotoId = SectionId.normalise(gotoId);
    }

    public Optional<Consequence> effect() {
        return Optional.ofNullable(consequence);
    }
}
