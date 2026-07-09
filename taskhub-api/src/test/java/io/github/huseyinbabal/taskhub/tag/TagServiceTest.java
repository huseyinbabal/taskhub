package io.github.huseyinbabal.taskhub.tag;

import java.util.List;

import io.github.huseyinbabal.taskhub.common.DuplicateResourceException;
import io.github.huseyinbabal.taskhub.common.PageResponse;
import io.github.huseyinbabal.taskhub.tag.dto.TagRequest;
import io.github.huseyinbabal.taskhub.tag.dto.TagResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for tag CRUD (SPEC §Session 2): create, duplicate 409, list. */
@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    TagRepository tagRepository;

    TagService tagService;

    @BeforeEach
    void setUp() {
        tagService = new TagService(tagRepository, new TagMapper());
    }

    @Test
    void create_newName_persistsTag() {
        when(tagRepository.existsByName("urgent")).thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        TagResponse response = tagService.create(new TagRequest("urgent", "#ff0000"));

        assertThat(response.name()).isEqualTo("urgent");
        assertThat(response.color()).isEqualTo("#ff0000");
    }

    @Test
    void create_duplicateName_throwsConflict() {
        when(tagRepository.existsByName("urgent")).thenReturn(true);

        assertThatThrownBy(() -> tagService.create(new TagRequest("urgent", "#ff0000")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void list_returnsPageOfTags() {
        when(tagRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new Tag("urgent", "#ff0000"))));

        PageResponse<TagResponse> response = tagService.list(0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }
}
