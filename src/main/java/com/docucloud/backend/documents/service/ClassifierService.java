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

        // Si ya tiene categoría manual, no sobreescribir
        if (doc.getClassification() != null &&
                Boolean.FALSE.equals(doc.getClassification().getIsAutomaticallyAssigned())) {
            log.info("⏭️ Doc {} ya tiene categoría manual, saltando AI", documentId);
            return;
        }

        try {
            ClassifyResponse resp = callClassifier(fileName);

            String categoryName = resp.category() != null && !resp.category().isBlank()
                    ? resp.category().trim()
                    : "Otros";
            double confidence = resp.confidence();

            log.info("🧠 AI: {} → {} (conf: {})", fileName, categoryName, confidence);

            Category category = resolveCategory(userId, categoryName);
            if (category == null) {
                markAsUnclassified(
                        doc,
                        "No existe categoría '" + categoryName + "' para user=" + userId
                );
                return;
            }

            saveClassification(doc, category, confidence);
            log.info("✅ Clasificación guardada: doc={} → cat='{}' conf={}",
                    documentId, categoryName, confidence);

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

        return resp != null ? resp : new ClassifyResponse("Otros", 0.0);
    }

    private Category resolveCategory(Long userId, String categoryName) {
        return categoryRepo.findByOwnerUserIdAndName(userId, categoryName)
                .orElse(null);
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
}