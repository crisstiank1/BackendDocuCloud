package com.docucloud.backend.searchhistory.controller;

import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.searchhistory.dto.response.SearchHistoryResponse;
import com.docucloud.backend.searchhistory.service.SearchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search-history")
@RequiredArgsConstructor
public class SearchHistoryController {

    private final SearchHistoryService searchHistoryService;

    private Long getUserId(Authentication auth) {
        return ((UserDetailsImpl) auth.getPrincipal()).getId();
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
