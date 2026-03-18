package com.docucloud.backend.documents.service;

import com.docucloud.backend.documents.dto.request.CreateCategoryRequest;
import com.docucloud.backend.documents.dto.response.CategoryResponse;
import com.docucloud.backend.documents.model.Category;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentCategory;
import com.docucloud.backend.documents.repository.CategoryRepository;
import com.docucloud.backend.documents.repository.DocumentCategoryRepository;
import com.docucloud.backend.documents.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.docucloud.backend.users.model.User;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final DocumentRepository documentRepository;
    private final DocumentCategoryRepository documentCategoryRepository;

    @Transactional
    public void createDefaultCategories(User user) {
        List<Object[]> defaults = List.of(
                new Object[]{"Facturas",   "#f59e0b"},
                new Object[]{"Contratos",  "#6366f1"},
                new Object[]{"Informes",   "#3b82f6"},
                new Object[]{"Personal",   "#10b981"},
                new Object[]{"Legal",      "#ef4444"},
                new Object[]{"Proyectos",  "#f97316"},
                new Object[]{"Otros",      "#8b5cf6"}
        );

        for (Object[] cat : defaults) {
            Category category = new Category();
            category.setOwnerUserId(user.getId());
            category.setName((String) cat[0]);
            category.setColor((String) cat[1]);
            categoryRepository.save(category);
        }
    }

    // ─── Listar ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(Long userId) {
        return categoryRepository
                .findByOwnerUserIdOrderByNameAsc(userId)
                .stream()
                .map(c -> CategoryResponse.from(
                        c,
                        categoryRepository.countDocumentsByCategoryId(userId, c.getId())
                ))
                .toList();
    }

    // ─── Crear ────────────────────────────────────────────────────────────────

    @Transactional
    public CategoryResponse createCategory(Long userId, CreateCategoryRequest request) {
        if (categoryRepository.existsByOwnerUserIdAndName(userId, request.name())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una categoría con el nombre '" + request.name() + "'"
            );
        }

        Category category = new Category();
        category.setOwnerUserId(userId);
        category.setName(request.name().trim());
        category.setColor(request.color());
        category = categoryRepository.save(category);

        log.info("🏷️ Category created - user={} name={}", userId, request.name());
        return CategoryResponse.from(category, 0);
    }

    // ─── Eliminar ─────────────────────────────────────────────────────────────

    @Transactional
    public void deleteCategory(Long userId, Long categoryId) {
        Category category = categoryRepository
                .findByIdAndOwnerUserId(categoryId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        // Elimina las clasificaciones asociadas antes de borrar la categoría
        documentCategoryRepository
                .findByCategory_IdAndDocument_OwnerUserIdAndDocument_DeletedAtIsNull(categoryId, userId)
                .forEach(documentCategoryRepository::delete);

        categoryRepository.delete(category);
        log.info("🗑️ Category deleted - user={} categoryId={}", userId, categoryId);
    }

    // ─── Asignar a documento ──────────────────────────────────────────────────

    @Transactional
    public void assignCategory(Long userId, Long documentId, Long categoryId) {
        Category category = categoryRepository
                .findByIdAndOwnerUserId(categoryId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        Document doc = documentRepository
                .findByIdAndOwnerUserIdAndDeletedAtIsNull(documentId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no encontrado"));

        DocumentCategory classification = doc.getClassification();
        if (classification == null) {
            classification = new DocumentCategory();
            classification.setDocument(doc);
        }
        classification.setCategory(category);
        classification.setIsAutomaticallyAssigned(false);
        classification.setConfidenceScore(null);
        documentCategoryRepository.save(classification);

        log.info("📂 Category assigned - user={} doc={} category={}", userId, documentId, categoryId);
    }

    // ─── Quitar de documento ──────────────────────────────────────────────────

    @Transactional
    public void removeCategory(Long userId, Long documentId) {
        documentRepository
                .findByIdAndOwnerUserIdAndDeletedAtIsNull(documentId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no encontrado"));

        documentCategoryRepository.deleteByDocument_Id(documentId);

        log.info("📂 Category removed - user={} doc={}", userId, documentId);
    }

    // ─── Actualizar ───────────────────────────────────────────────────────────

    @Transactional
    public CategoryResponse updateCategory(Long userId, Long categoryId, CreateCategoryRequest request) {
        Category category = categoryRepository
                .findByIdAndOwnerUserId(categoryId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        category.setName(request.name().trim());
        category.setColor(request.color());
        categoryRepository.save(category);

        log.info("✏️ Category updated - user={} categoryId={}", userId, categoryId);
        return CategoryResponse.from(category,
                categoryRepository.countDocumentsByCategoryId(userId, categoryId));
    }
}