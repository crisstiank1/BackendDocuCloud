package com.docucloud.backend.tags.service;

import com.docucloud.backend.audit.annotation.Audited;
import com.docucloud.backend.audit.service.AuditService;
import com.docucloud.backend.tags.model.Tag;
import com.docucloud.backend.tags.repository.TagRepository;
import com.docucloud.backend.tags.dto.response.TagResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private final AuditService  auditService;  // ✅
    private final ObjectMapper  objectMapper;  // ✅

    public List<TagResponse> getUserTags(Long userId) {
        return tagRepository.findByUserId(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Audited(action = "CREATE_TAG", resourceType = "Tag")
    public TagResponse createTag(String name, Long userId) {
        if (tagRepository.findByNameAndUserId(name, userId).isPresent())
            throw new IllegalArgumentException("Tag exists");
        Tag tag = Tag.builder().name(name).userId(userId).build();
        return toResponse(tagRepository.save(tag));
    }

    // ✅ Sin @Audited — auditamos manualmente para poder incluir el nombre
    public void deleteTag(Long id, Long userId) {
        Tag tag = tagRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found or not owned"));

        boolean success = true;
        try {
            tagRepository.deleteByIdAndUserId(id, userId);
        } catch (Exception ex) {
            success = false;
            throw ex;
        } finally {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("name", tag.getName());
            auditService.logBusiness(userId, "DELETE_TAG", "Tag", id, success, details);
        }
    }

    private TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}