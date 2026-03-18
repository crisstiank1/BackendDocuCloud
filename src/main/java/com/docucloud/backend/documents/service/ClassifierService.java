package com.docucloud.backend.documents.service;

import com.docucloud.backend.documents.model.Category;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentCategory;
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


    public void classifyAndAssign(Long documentId, String fileName, Long userId) {
        try {
            // 1. Recuperar el documento
            Document doc = documentRepo.findById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + documentId));

            // 2. Si ya tiene categoría manual, no sobreescribir
            if (doc.getClassification() != null &&
                    Boolean.FALSE.equals(doc.getClassification().getIsAutomaticallyAssigned())) {
                log.info("⏭️ Doc {} ya tiene categoría manual, saltando clasificación AI", documentId);
                return;
            }

            // 3. Llama microservicio Python
            Map<String, String> payload = Map.of("file_name", fileName);
            ClassifyResponse resp = restTemplate.postForObject(
                    classifierUrl + "/classify",
                    payload,
                    ClassifyResponse.class
            );

            String categoryName = resp != null && resp.category() != null
                    ? resp.category()
                    : "Otros";
            double confidence = resp != null ? resp.confidence() : 0.0;

            log.info("🧠 AI: {} → {} (conf: {})", fileName, categoryName, confidence);

            // 4. Busca categoría del usuario o la crea
            Category category = categoryRepo
                    .findByOwnerUserIdAndName(userId, categoryName)
                    .orElseGet(() -> {
                        log.info("🎨 Creando categoría AI nueva: user={} name={}", userId, categoryName);
                        Category newCat = new Category();
                        newCat.setOwnerUserId(userId);
                        newCat.setName(categoryName);
                        newCat.setColor("#6b7280");
                        return categoryRepo.save(newCat);
                    });

            // 5. Crea o actualiza la clasificación
            DocumentCategory dc = doc.getClassification();
            if (dc == null) {
                dc = new DocumentCategory();
                dc.setDocument(doc);
            }
            dc.setCategory(category);
            dc.setIsAutomaticallyAssigned(true);
            dc.setConfidenceScore(BigDecimal.valueOf(confidence));
            docCatRepo.save(dc);

            log.info("✅ AI guardado: doc={} → cat='{}' conf={}", documentId, categoryName, confidence);

        } catch (Exception e) {
            log.warn("⚠️ Classifier falló para '{}': {}", fileName, e.getMessage());
        }
    }

    @Async
    @Transactional
    public void classifyAndAssignAsync(Long documentId, String fileName, Long userId) {
        classifyAndAssign(documentId, fileName, userId);
    }

    private record ClassifyResponse(String category, double confidence) {}
}