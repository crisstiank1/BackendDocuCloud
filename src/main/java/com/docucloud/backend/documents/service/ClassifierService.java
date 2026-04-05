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

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassifierService {

    private static final String FALLBACK_CATEGORY = "Otros";

    private final CategoryRepository categoryRepo;
    private final DocumentCategoryRepository docCatRepo;
    private final DocumentRepository documentRepo;
    private final RestTemplate restTemplate;

    @Value("${classifier.url:http://localhost:8001}")
    private String classifierUrl;

    @Async
    @Transactional
    public void classifyAndAssignAsync(Long documentId, String fileName, Long userId) {
        Document doc = documentRepo.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Documento no encontrado: " + documentId));

        if (doc.getClassification() != null &&
                Boolean.FALSE.equals(doc.getClassification().getIsAutomaticallyAssigned())) {
            log.info("⏭️ Doc {} ya tiene categoría manual, saltando AI", documentId);
            return;
        }

        try {
            ClassifyResponse resp = callClassifier(fileName);

            String predictedName = normalizeCategoryName(resp.category());
            double predictedConfidence = resp.confidence();

            ResolvedCategory resolved = resolvePredictedOrFallback(userId, predictedName);

            if (resolved.category() == null) {
                markAsUnclassified(
                        doc,
                        "No existe categoría predicha ni fallback '" + FALLBACK_CATEGORY + "' para user=" + userId
                );
                return;
            }

            double appliedConfidence = resolved.fallbackUsed() ? 0.0 : predictedConfidence;

            saveClassification(doc, resolved.category(), appliedConfidence);

            log.info(
                    "✅ Clasificación guardada: doc={} predicted='{}' assigned='{}' conf={}",
                    documentId,
                    predictedName,
                    resolved.category().getName(),
                    appliedConfidence
            );

        } catch (Exception e) {
            markAsFailed(doc, e.getMessage());
            log.warn("⚠️ Classifier falló para '{}': {}", fileName, e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public ClassificationStatsResponse getStats(Long userId) {
        long total = documentRepo.countActiveByOwnerUserId(userId);
        long classified = documentRepo.countClassifiedByOwnerUserId(userId);
        long failed = documentRepo.countByOwnerUserIdAndStatusFiltered(userId, DocumentStatus.FAILED);
        long pending = Math.max(0, total - classified - failed);
        long categories = categoryRepo.countByOwnerUserId(userId);

        return new ClassificationStatsResponse(total, classified, pending, failed, categories);
    }

    private ClassifyResponse callClassifier(String fileName) {
        Map<String, String> payload = Map.of("file_name", fileName);

        ClassifyResponse resp = restTemplate.postForObject(
                classifierUrl + "/classify",
                payload,
                ClassifyResponse.class
        );

        return resp != null ? resp : new ClassifyResponse(null, 0.0);
    }

    private String normalizeCategoryName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.trim();
    }

    private ResolvedCategory resolvePredictedOrFallback(Long userId, String predictedName) {
        if (predictedName != null) {
            Category predicted = categoryRepo
                    .findByOwnerUserIdAndNameIgnoreCase(userId, predictedName)
                    .orElse(null);

            if (predicted != null) {
                return new ResolvedCategory(predicted, false);
            }
        }

        Category otros = categoryRepo
                .findByOwnerUserIdAndNameIgnoreCase(userId, FALLBACK_CATEGORY)
                .orElse(null);

        if (otros != null) {
            return new ResolvedCategory(otros, true);
        }

        return new ResolvedCategory(null, true);
    }

    private void saveClassification(Document doc, Category category, double confidence) {
        DocumentCategory dc = doc.getClassification() != null
                ? doc.getClassification()
                : new DocumentCategory();

        dc.setDocument(doc);
        dc.setCategory(category);
        dc.setIsAutomaticallyAssigned(true);
        dc.setConfidenceScore(BigDecimal.valueOf(confidence));

        doc.setClassification(dc);
        doc.setStatus(DocumentStatus.AVAILABLE);

        docCatRepo.save(dc);
        documentRepo.save(doc);
    }

    private void markAsFailed(Document doc, String reason) {
        doc.setStatus(DocumentStatus.FAILED);
        documentRepo.save(doc);
        log.warn("❌ Doc {} marcado como FAILED: {}", doc.getId(), reason);
    }

    private void markAsUnclassified(Document doc, String reason) {
        DocumentCategory existing = doc.getClassification();

        if (existing != null && Boolean.TRUE.equals(existing.getIsAutomaticallyAssigned())) {
            doc.setClassification(null);
            docCatRepo.delete(existing);
        }

        doc.setStatus(DocumentStatus.AVAILABLE);
        documentRepo.save(doc);

        log.info("📄 Doc {} queda SIN CLASIFICAR: {}", doc.getId(), reason);
    }

    private record ClassifyResponse(String category, double confidence) {}
    private record ResolvedCategory(Category category, boolean fallbackUsed) {}
}