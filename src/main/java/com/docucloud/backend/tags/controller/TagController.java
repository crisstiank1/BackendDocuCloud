package com.docucloud.backend.tags.controller;

import com.docucloud.backend.tags.service.TagService;
import com.docucloud.backend.tags.dto.response.TagResponse;
import com.docucloud.backend.users.repository.UserRepository;
import com.docucloud.backend.users.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {
    private final TagService tagService;
    private final UserRepository userRepository;

    @GetMapping
    public List<TagResponse> getTags(Authentication auth) {
        String email = auth.getName();
        Long userId = getUserId(email);
        return tagService.getUserTags(userId);
    }

    @PostMapping
    public TagResponse createTag(@RequestBody Map<String, String> body, Authentication auth) {
        String email = auth.getName();
        Long userId = getUserId(email);
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name required");
        }
        return tagService.createTag(name.trim(), userId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id, Authentication auth) {
        String email = auth.getName();
        Long userId = getUserId(email);
        tagService.deleteTag(id, userId);
        return ResponseEntity.noContent().build();  // 204 No Content
    }

    private Long getUserId(String email) {
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
