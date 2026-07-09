package io.github.huseyinbabal.taskhub.tag;

import io.github.huseyinbabal.taskhub.tag.dto.TagResponse;
import org.springframework.stereotype.Component;

/** Hand-written tag ↔ DTO mapping (consistent with the project's mapping approach). */
@Component
public class TagMapper {

    public TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getColor());
    }
}
