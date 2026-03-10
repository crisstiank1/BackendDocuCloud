package com.docucloud.backend.search.controller;

import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.search.dto.response.SearchHistoryResponse;
import com.docucloud.backend.search.service.SearchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search-history")
@RequiredArgsConstructor
public class SearchHistoryController {

    private final SearchHistoryService searchHistoryService;

    private Long getUserId(Authentication auth) {
        return ((UserDetailsImpl) auth.getPrincipal()).getId();
    }

    @GetMapping("/suggestions")
    public ResponseEntity<Map<String, Object>> getSuggestions(
            Authentication auth, @RequestParam String prefix) {
        Long userId = getUserId(auth);
        List<String> suggestions = searchHistoryService.getSuggestions(userId, prefix);

        return ResponseEntity.ok(Map.of(
                "suggestions", suggestions,
                "count", suggestions.size(),
                "prefix", prefix
        ));
    }

    @GetMapping
    public ResponseEntity<List<SearchHistoryResponse>> getRecent(Authentication auth) {
        return ResponseEntity.ok(searchHistoryService.getRecentSearches(getUserId(auth)));
    }

    @DeleteMapping("/{historyId}")
    public ResponseEntity<Void> deleteOne(@PathVariable Long historyId, Authentication auth) {
        searchHistoryService.deleteOne(getUserId(auth), historyId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll(Authentication auth) {
        searchHistoryService.clearAll(getUserId(auth));
        return ResponseEntity.noContent().build();
    }
}
