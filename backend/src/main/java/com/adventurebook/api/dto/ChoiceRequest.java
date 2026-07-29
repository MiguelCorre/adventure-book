package com.adventurebook.api.dto;

import jakarta.validation.constraints.PositiveOrZero;

/** @param optionIndex position of the chosen option within the current section */
public record ChoiceRequest(@PositiveOrZero int optionIndex) {
}
