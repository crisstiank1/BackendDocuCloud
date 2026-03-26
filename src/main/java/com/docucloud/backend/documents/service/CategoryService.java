package com.docucloud.backend.documents.service;

import com.docucloud.backend.audit.annotation.Audited;
import com.docucloud.backend.audit.service.AuditService;
import com.docucloud.backend.documents.dto.request.CreateCategoryRequest;
import com.docucloud.backend.documents.dto.response.CategoryResponse;
import com.docucloud.backend.documents.model.Category;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentCategory;
import com.docucloud.backend.documents.repository.CategoryRepository;
import com.docucloud.backend.documents.repository.DocumentCategoryRepository;
import com.docucloud.backend.documents.repository.DocumentRepository;
import com.docucloud.backend.users.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository           categoryRepository;
    private final DocumentRepository           documentRepository;
    private final DocumentCategoryRepository   documentCategoryRepository;
    private final AuditService                 auditService;
    private final ObjectMapper                 objectMapper;

    // ─── Categorías por defecto ───────────────────────────────────────────────

    @Transactional
    public void createDefaultCategories(User user) {
        List<Category> defaults = List.of(
                buildCategory(user.getId(), "Facturas",  "#f59e0b"),
                buildCategory(user.getId(), "Contratos", "#6366f1"),
                buildCategory(user.getId(), "Informes",  "#3b82f6"),
                buildCategory(user.getId(), "Personal",  "#10b981"),
                buildCategory(user.getId(), "Legal",     "#ef4444"),
                buildCategory(user.getId(), "Proyectos", "#f97316"),
                buildCategory(user.getId(), "Otros",     "#8b5cf6")
        );
        categoryRepository.saveAll(defaults);   // ✅ 1 sola query en vez de 7
    }

    private Category buildCategory(Long userId, String name, String color) {
        Category c = new Category();
        c.setOwnerUserId(userId);
        c.setName(name);
        c.setColor(color);
        return c;
    }

    // ─── Listar ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(Long userId) {
        List<Category> categories = categoryRepository.findByOwnerUserIdOrderByNameAsc(userId);

        // ✅ Una sola query para todos los conteos — evita N+1
        Map<Long, Long> countMap = categoryRepository
                .countDocumentsGroupedByCategory(userId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        return categories.stream()
                .map(c -> CategoryResponse.from(c, countMap.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    // ─── Crear ────────────────────────────────────────────────────────────────

    @Audited(action = "CREATE_CATEGORY", resourceType = "Category")
    @Transactional
    public CategoryResponse createCategory(Long userId, CreateCategoryRequest request) {
        if (categoryRepository.existsByOwnerUserIdAndName(userId, request.name())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una categoría con el nombre '" + request.name() + "'");
        }

        Category category = new Category();
        category.setOwnerUserId(userId);
        category.setName(request.name().trim());
        category.setColor(request.color());
        category = categoryRepository.save(category);

        log.info("🏷️ Category created - user={} name={}", userId, request.name());
        return CategoryResponse.from(category, 0L);
    }

    // ─── Actualizar ───────────────────────────────────────────────────────────

    @Audited(action = "UPDATE_CATEGORY", resourceType = "Category", resourceIdArgIndex = 1)
    @Transactional
    public CategoryResponse updateCategory(Long userId, Long categoryId,
                                           CreateCategoryRequest request) {
        Category category = categoryRepository
                .findByIdAndOwnerUserId(categoryId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        category.setName(request.name().trim());
        category.setColor(request.color());
        categoryRepository.save(category);

        log.info("✏️ Category updated - user={} categoryId={}", userId, categoryId);

        // ✅ Reutiliza el conteo individual solo aquí — es 1 sola categoría
        long count = categoryRepository.countDocumentsByCategoryId(userId, categoryId);
        return CategoryResponse.from(category, count);
    }

    // ─── Eliminar ─────────────────────────────────────────────────────────────

    // ✅ Sin @Audited — se registra manualmente para poder incluir el nombre
    @Transactional
    public void deleteCategory(Long userId, Long categoryId) {
        Category category = categoryRepository
                .findByIdAndOwnerUserId(categoryId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        boolean success = true;
        try {
            // ✅ Una sola query DELETE en vez de N deletes individuales
            documentCategoryRepository.deleteByCategory_Id(categoryId);
            categoryRepository.delete(category);
            log.info("🗑️ Category deleted - user={} categoryId={}", userId, categoryId);
        } catch (Exception ex) {
            success = false;
            throw ex;
        } finally {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("name", category.getName());
            auditService.logBusiness(userId, "DELETE_CATEGORY", "Category", categoryId, success, details);
        }
    }

    // ─── Eliminar todas las categorías de un usuario (al eliminar cuenta) ─────

    // ✅ Método añadido por el remoto — se mantiene íntegro
    @Transactional
    public void deleteCategoriesByUserId(Long userId) {
        List<Category> categories = categoryRepository.findByOwnerUserId(userId);
        if (categories.isEmpty()) return;

        List<Long> ids = categories.stream().map(Category::getId).toList();

        documentCategoryRepository.deleteByCategory_IdIn(ids);
        categoryRepository.deleteAll(categories);

        log.info("🗑️ Categorías eliminadas para userId={} → {} categorías", userId, categories.size());
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
}