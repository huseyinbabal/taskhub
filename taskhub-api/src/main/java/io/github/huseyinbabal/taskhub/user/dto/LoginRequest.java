package io.github.huseyinbabal.taskhub.user.dto;

import jakarta.validation.constraints.NotBlank;

/** Login payload (SPEC §Session 2). */
public record LoginRequest(

        @NotBlank
        String username,

        @NotBlank
        String password) {
}
