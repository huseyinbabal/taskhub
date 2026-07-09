package io.github.huseyinbabal.taskhub.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registration payload (SPEC §Session 2). All fields validated at the edge. */
public record RegisterRequest(

        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 3, max = 100)
        String username,

        @NotBlank @Size(min = 8, max = 100)
        String password) {
}
