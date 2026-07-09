package io.github.huseyinbabal.taskhub.tag;

import java.net.URI;

import io.github.huseyinbabal.taskhub.common.PageResponse;
import io.github.huseyinbabal.taskhub.tag.dto.TagRequest;
import io.github.huseyinbabal.taskhub.tag.dto.TagResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tag endpoints (SPEC §Session 2). Authenticated; tags are not user-scoped. */
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public PageResponse<TagResponse> list(@RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        return tagService.list(page, size);
    }

    @PostMapping
    public ResponseEntity<TagResponse> create(@Valid @RequestBody TagRequest request) {
        TagResponse created = tagService.create(request);
        return ResponseEntity.created(URI.create("/api/tags/" + created.id())).body(created);
    }
}
