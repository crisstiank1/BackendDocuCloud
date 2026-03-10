package com.docucloud.backend.tags.service;

import com.docucloud.backend.tags.model.Tag;
import com.docucloud.backend.tags.repository.TagRepository;
import com.docucloud.backend.tags.dto.response.TagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TagService {
    private final TagRepository tagRepository;

    public List<TagResponse> getUserTags(Long userId) {
        return tagRepository.findByUserId(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public TagResponse createTag(String name, Long userId) {
        if (tagRepository.findByNameAndUserId(name, userId).isPresent())
            throw new IllegalArgumentException("Tag exists");
        Tag tag = Tag.builder().name(name).userId(userId).build();
        return toResponse(tagRepository.save(tag));
    }

    // AGREGAR método en TagService
    public void deleteTag(Long id, Long userId) {
        if (!tagRepository.existsByIdAndUserId(id, userId)) {
            throw new IllegalArgumentException("Tag not found or not owned");
        }
        tagRepository.deleteByIdAndUserId(id, userId);
    }


    private TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}
