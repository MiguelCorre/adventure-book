package com.adventurebook.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param bookSlug book to open
 * @param fromSave when true, continue from saved progress instead of the beginning
 */
public record StartGameRequest(@NotBlank String bookSlug, boolean fromSave) {
}
