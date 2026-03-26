package com.docucloud.backend.tags.controller;

import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.tags.dto.response.TagResponse;
import com.docucloud.backend.tags.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    private Long getUserId(Authentication auth) {
        return ((UserDetailsImpl) auth.getPrincipal()).getId();  // ✅ sin query extra a BD
    }

    @GetMapping
    public List<TagResponse> getTags(Authentication auth) {
        return tagService.getUserTags(getUserId(auth));
    }

    @PostMapping
    public TagResponse createTag(@RequestBody Map<String, String> body, Authentication auth) {
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name required");
        }
        return tagService.createTag(name.trim(), getUserId(auth));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id, Authentication auth) {
        tagService.deleteTag(id, getUserId(auth));
        return ResponseEntity.noContent().build();
    }
}