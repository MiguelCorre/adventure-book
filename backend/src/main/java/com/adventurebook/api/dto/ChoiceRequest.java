package com.adventurebook.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** @param optionIndex position of the chosen option within the current section */
public record ChoiceRequest(@NotNull @PositiveOrZero Integer optionIndex) {
}
