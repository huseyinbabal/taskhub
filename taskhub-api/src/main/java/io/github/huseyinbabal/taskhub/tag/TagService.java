package io.github.huseyinbabal.taskhub.tag;

import io.github.huseyinbabal.taskhub.common.DuplicateResourceException;
import io.github.huseyinbabal.taskhub.common.PageRequests;
import io.github.huseyinbabal.taskhub.common.PageResponse;
import io.github.huseyinbabal.taskhub.tag.dto.TagRequest;
import io.github.huseyinbabal.taskhub.tag.dto.TagResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tag CRUD (SPEC §Session 2). Tags are shared across the system (no per-user
 * ownership); names are unique.
 */
@Service
public class TagService {

    private final TagRepository tagRepository;
    private final TagMapper tagMapper;

    public TagService(TagRepository tagRepository, TagMapper tagMapper) {
        this.tagRepository = tagRepository;
        this.tagMapper = tagMapper;
    }

    @Transactional
    public TagResponse create(TagRequest request) {
        if (tagRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Tag already exists: " + request.name());
        }
        return tagMapper.toResponse(tagRepository.save(new Tag(request.name(), request.color())));
    }

    @Transactional(readOnly = true)
    public PageResponse<TagResponse> list(Integer page, Integer size) {
        PageRequest pageable = PageRequests.of(page, size, Sort.by("name"));
        return PageResponse.from(tagRepository.findAll(pageable).map(tagMapper::toResponse));
    }
}
