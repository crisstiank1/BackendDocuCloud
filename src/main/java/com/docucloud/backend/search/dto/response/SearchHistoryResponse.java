package com.docucloud.backend.search.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SearchHistoryResponse {
    private Long id;
    private String query;
    private Instant createdAt;
}
