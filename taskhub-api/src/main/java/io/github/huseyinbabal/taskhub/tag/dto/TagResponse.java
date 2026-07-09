package io.github.huseyinbabal.taskhub.tag.dto;

/** API view of a tag (SPEC §4 boundary — never the entity). */
public record TagResponse(Long id, String name, String color) {
}
