package com.docucloud.backend.documents.controller;

import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.documents.dto.request.CreateCategoryRequest;
import com.docucloud.backend.documents.dto.response.CategoryResponse;
import com.docucloud.backend.documents.dto.response.ClassificationStatsResponse;
import com.docucloud.backend.documents.service.CategoryService;
import com.docucloud.backend.documents.service.ClassifierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final ClassifierService classifierService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> list(
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(categoryService.listCategories(user.getId()));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CreateCategoryRequest request,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(categoryService.createCategory(user.getId(), request));
    }

    @PatchMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable Long categoryId,
            @Valid @RequestBody CreateCategoryRequest request,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(
                categoryService.updateCategory(user.getId(), categoryId, request));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long categoryId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        categoryService.deleteCategory(user.getId(), categoryId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{categoryId}/documents/{documentId}")
    public ResponseEntity<Void> assign(
            @PathVariable Long categoryId,
            @PathVariable Long documentId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        log.info("🔍 assign - userId={} docId={} categoryId={}", user.getId(), documentId, categoryId);
        categoryService.assignCategory(user.getId(), documentId, categoryId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> removeFromDocument(
            @PathVariable Long documentId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        log.info("🗑️ removeCategory - userId={} docId={}", user.getId(), documentId);
        categoryService.removeCategory(user.getId(), documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/classification/stats")
    public ResponseEntity<ClassificationStatsResponse> getClassificationStats(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(classifierService.getStats(userDetails.getId()));
    }
}