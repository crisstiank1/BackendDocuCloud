package com.docucloud.backend.search.service;

import com.docucloud.backend.search.dto.response.SearchHistoryResponse;
import com.docucloud.backend.search.model.SearchHistory;
import com.docucloud.backend.search.repository.SearchHistoryRepository;
import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchHistoryService {

    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;
    private final Map<String, List<String>> suggestionCache = new ConcurrentHashMap<>(); // 5min TTL

    @Transactional
    public void saveSearch(Long userId, String query) {
        if (query == null || query.trim().length() < 2) return;

        String normalized = query.trim().toLowerCase();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));


        List<SearchHistory> current = searchHistoryRepository.findTop10ByUser_IdOrderByCreatedAtDesc(userId);
        if (!current.isEmpty() && current.get(0).getQuery().equalsIgnoreCase(normalized)) {
            return;
        }

        SearchHistory history = SearchHistory.builder().user(user).query(normalized).build();
        searchHistoryRepository.save(history);

        // Cleanup
        List<SearchHistory> all = searchHistoryRepository.findByUser_IdOrderByCreatedAtDesc(userId);
        if (all.size() > 10) {
            searchHistoryRepository.deleteAll(all.subList(10, all.size()));
        }

        log.info("🔍 Search saved - user={} query='{}'", userId, normalized);
    }

    public List<String> getSuggestions(Long userId, String prefix) {
        if (prefix == null || prefix.trim().length() < 2) return List.of();

        String cacheKey = userId + ":" + prefix.toLowerCase().trim();
        return suggestionCache.computeIfAbsent(cacheKey, k -> {
            List<String> suggestions = searchHistoryRepository.findSmartSuggestions(userId, prefix)
                    .stream()
                    .limit(8)  // Máx 8
                    .map(SearchHistory::getQuery)
                    .filter(s -> !s.equalsIgnoreCase(prefix))  // No repetir input
                    .collect(Collectors.toList());

            // Cache 5min
            CompletableFuture.delayedExecutor(5, TimeUnit.MINUTES).execute(() ->
                    suggestionCache.remove(cacheKey));
            return suggestions;
        });
    }

    public List<SearchHistoryResponse> getRecentSearches(Long userId) {
        return searchHistoryRepository.findTop10ByUser_IdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteOne(Long userId, Long historyId) {
        SearchHistory history = searchHistoryRepository.findByIdAndUser_Id(historyId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Historial no encontrado"));
        searchHistoryRepository.delete(history);
    }

    @Transactional
    public void clearAll(Long userId) {
        searchHistoryRepository.deleteByUser_Id(userId);
    }

    private SearchHistoryResponse toResponse(SearchHistory h) {
        return SearchHistoryResponse.builder()
                .id(h.getId()).query(h.getQuery()).createdAt(h.getCreatedAt()).build();
    }
}