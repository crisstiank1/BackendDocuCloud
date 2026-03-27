package com.docucloud.backend.documents.service;

import com.docucloud.backend.documents.dto.response.ClassificationStatsResponse;
import com.docucloud.backend.documents.model.Category;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentCategory;
import com.docucloud.backend.documents.model.DocumentStatus;
import com.docucloud.backend.documents.repository.CategoryRepository;
import com.docucloud.backend.documents.repository.DocumentCategoryRepository;
import com.docucloud.backend.documents.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassifierService {

    private final CategoryRepository         categoryRepo;
    private final DocumentCategoryRepository docCatRepo;
    private final DocumentRepository         documentRepo;
    private final RestTemplate               restTemplate;

    @Value("${classifier.url:http://localhost:8001}")
    private String classifierUrl;

    // ── Punto de entrada asíncrono ─────────────────────────────────────────────
    // @Async funciona porque DocumentService llama este método desde FUERA.
    // La transacción sí se activa porque Spring intercepta el proxy correctamente.
    @Async
    @Transactional
    public void classifyAndAssignAsync(Long documentId, String fileName, Long userId) {
        Document doc = documentRepo.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Documento no encontrado: " + documentId));

        // Si ya tiene categoría manual, no sobreescribir
        if (doc.getClassification() != null &&
                Boolean.FALSE.equals(doc.getClassification().getIsAutomaticallyAssigned())) {
            log.info("⏭️ Doc {} ya tiene categoría manual, saltando AI", documentId);
            return;
        }

        try {
            // Una sola llamada al microservicio — evita llamar dos veces
            ClassifyResponse resp = callClassifier(fileName);

            String categoryName = resp.category() != null ? resp.category() : "Otros";
            double confidence   = resp.confidence();

            log.info("🧠 AI: {} → {} (conf: {})", fileName, categoryName, confidence);

            Category category = resolveCategory(userId, categoryName);
            if (category == null) {
                markAsFailed(doc, "No existe categoría destino para user=" + userId);
                return;
            }

            saveClassification(doc, category, confidence);
            log.info("✅ Clasificación guardada: doc={} → cat='{}' conf={}",
                    documentId, categoryName, confidence);

        } catch (Exception e) {
            // Antes: solo log.warn → doc quedaba en limbo para siempre
            // Ahora: se marca FAILED para que el frontend lo muestre correctamente
            markAsFailed(doc, e.getMessage());
            log.warn("⚠️ Classifier falló para '{}': {}", fileName, e.getMessage());
        }
    }

    // ── Estadísticas reales para el frontend ───────────────────────────────────
    @Transactional(readOnly = true)
    public ClassificationStatsResponse getStats(Long userId) {
        long total      = documentRepo.countActiveByOwnerUserId(userId);
        long classified = documentRepo.countClassifiedByOwnerUserId(userId);
        long failed     = documentRepo.countByOwnerUserIdAndStatusFiltered(userId, DocumentStatus.FAILED);
        long pending    = Math.max(0, total - classified - failed); // ← nunca negativo
        long categories = categoryRepo.countByOwnerUserId(userId);

        return new ClassificationStatsResponse(total, classified, pending, failed, categories);
    }

    // ── Helpers privados ───────────────────────────────────────────────────────

    private ClassifyResponse callClassifier(String fileName) {
        Map<String, String> payload = Map.of("file_name", fileName);
        ClassifyResponse resp = restTemplate.postForObject(
                classifierUrl + "/classify", payload, ClassifyResponse.class);
        // Si el microservicio no responde, devuelve valores por defecto seguros
        return resp != null ? resp : new ClassifyResponse("Otros", 0.0);
    }

    private Category resolveCategory(Long userId, String categoryName) {
        // 1. Buscar la categoría exacta que sugirió la IA
        Optional<Category> exact = categoryRepo.findByOwnerUserIdAndName(userId, categoryName);
        if (exact.isPresent()) return exact.get();

        // 2. Si no existe, crearla automáticamente con color determinista
        // Así el usuario ve la categoría sugerida por la IA en su panel
        log.info("📂 Creando categoría automática '{}' para user={}", categoryName, userId);
        Category newCategory = new Category();
        newCategory.setOwnerUserId(userId);
        newCategory.setName(categoryName);
        newCategory.setColor(generateColorForName(categoryName));
        return categoryRepo.save(newCategory);
    }

    private void saveClassification(Document doc, Category category, double confidence) {
        DocumentCategory dc = doc.getClassification() != null
                ? doc.getClassification()
                : new DocumentCategory();

        dc.setDocument(doc);
        dc.setCategory(category);
        dc.setIsAutomaticallyAssigned(true);
        dc.setConfidenceScore(BigDecimal.valueOf(confidence));
        docCatRepo.save(dc);
    }

    private void markAsFailed(Document doc, String reason) {
        doc.setStatus(DocumentStatus.FAILED);
        documentRepo.save(doc);
        log.warn("❌ Doc {} marcado como FAILED: {}", doc.getId(), reason);
    }

    private record ClassifyResponse(String category, double confidence) {}

    private String generateColorForName(String name) {
        String[] palette = {
                "#6366f1", "#8b5cf6", "#ec4899", "#f59e0b",
                "#10b981", "#3b82f6", "#ef4444", "#14b8a6"
        };
        return palette[Math.abs(name.hashCode()) % palette.length];
    }
}