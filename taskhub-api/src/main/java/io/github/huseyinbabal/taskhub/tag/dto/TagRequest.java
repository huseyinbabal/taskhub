package io.github.huseyinbabal.taskhub.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create payload for a tag (SPEC §Session 2). Names are unique system-wide. */
public record TagRequest(

        @NotBlank @Size(max = 50)
        String name,

        @Size(max = 20)
        String color) {
}
