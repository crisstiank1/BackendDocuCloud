package com.docucloud.backend.documents.service;

import com.docucloud.backend.documents.model.Category;
import com.docucloud.backend.documents.model.DocumentCategory;
import com.docucloud.backend.documents.model.DocumentCategoryId;
import com.docucloud.backend.documents.repository.CategoryRepository;
import com.docucloud.backend.documents.repository.DocumentCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassifierService {

    private final CategoryRepository categoryRepo;
    private final DocumentCategoryRepository docCatRepo;
    private final RestTemplate restTemplate;

    @Value("${classifier.url:http://localhost:8001}")
    private String classifierUrl;

    public void classifyAndAssign(Long documentId, String fileName, Long userId) {
        try {
            // 1. Llama microservicio Python
            Map<String, String> payload = Map.of("file_name", fileName);
            ClassifyResponse resp = restTemplate.postForObject(
                    classifierUrl + "/classify",
                    payload,
                    ClassifyResponse.class
            );

            String categoryName = resp != null ? resp.category() : "Otros";
            double confidence   = resp != null ? resp.confidence() : 0.0;

            log.info("🧠 AI: {} → {} (conf: {})", fileName, categoryName, confidence);

            // 2. Busca categoría del usuario; si no existe la crea automáticamente
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

            // 3. Construye clave compuesta
            DocumentCategoryId dcId = new DocumentCategoryId(
                    documentId,
                    category.getId()
            );

            // 4. Evita duplicado si ya fue clasificado
            if (docCatRepo.existsById(dcId)) {
                log.info("⚠️ Ya clasificado: docId={} catId={}", documentId, category.getId());
                return;
            }

            // 5. Guarda clasificación AI
            DocumentCategory dc = DocumentCategory.builder()
                    .id(dcId)
                    .isAutomaticallyAssigned(true)
                    .confidenceScore(BigDecimal.valueOf(confidence))
                    .build();
            docCatRepo.save(dc);

            log.info("✅ AI guardado: doc={} → cat='{}' conf={}", documentId, categoryName, confidence);

        } catch (Exception e) {
            log.warn("⚠️ Classifier falló para '{}': {}", fileName, e.getMessage());
        }
    }

    private record ClassifyResponse(String category, double confidence) {}
}
