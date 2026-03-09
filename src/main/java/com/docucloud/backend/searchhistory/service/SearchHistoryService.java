package com.docucloud.backend.searchhistory.service;

import com.docucloud.backend.searchhistory.dto.response.SearchHistoryResponse;
import com.docucloud.backend.searchhistory.model.SearchHistory;
import com.docucloud.backend.searchhistory.repository.SearchHistoryRepository;
import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchHistoryService {

    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public void saveSearch(Long userId, String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        String normalized = query.trim();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        List<SearchHistory> current = searchHistoryRepository
                .findTop10ByUserIdOrderByCreatedAtDesc(userId);

        if (!current.isEmpty() && current.get(0).getQuery().equalsIgnoreCase(normalized)) {
            return;
        }

        SearchHistory history = SearchHistory.builder()
                .user(user)
                .query(normalized)
                .build();

        searchHistoryRepository.save(history);

        List<SearchHistory> all = searchHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);

        if (all.size() > 10) {
            List<SearchHistory> extra = all.subList(10, all.size());
            searchHistoryRepository.deleteAll(extra);
        }

        log.info("Search history guardado para userId={}, query={}", userId, normalized);
    }

    public List<SearchHistoryResponse> getRecentSearches(Long userId) {
        return searchHistoryRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteOne(Long userId, Long historyId) {
        SearchHistory history = searchHistoryRepository.findByIdAndUserId(historyId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Historial no encontrado"));

        searchHistoryRepository.delete(history);
    }

    @Transactional
    public void clearAll(Long userId) {
        searchHistoryRepository.deleteByUserId(userId);
    }

    private SearchHistoryResponse toResponse(SearchHistory h) {
        return SearchHistoryResponse.builder()
                .id(h.getId())
                .query(h.getQuery())
                .createdAt(h.getCreatedAt())
                .build();
    }
}
