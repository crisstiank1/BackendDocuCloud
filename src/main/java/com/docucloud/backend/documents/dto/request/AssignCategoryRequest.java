package com.docucloud.backend.documents.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssignCategoryRequest(Long categoryId) {}