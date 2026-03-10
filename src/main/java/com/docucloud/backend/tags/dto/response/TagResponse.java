package com.docucloud.backend.tags.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class TagResponse {
    private Long id;
    private String name;
}