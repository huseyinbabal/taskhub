package io.github.huseyinbabal.taskhub.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for a project (SPEC §Session 2). */
public record ProjectRequest(

        @NotBlank @Size(max = 150)
        String name,

        @Size(max = 2000)
        String description) {
}
