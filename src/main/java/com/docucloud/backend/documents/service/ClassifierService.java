package com.docucloud.backend.documents.service;

import com.docucloud.backend.documents.model.Category;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentCategory;
import com.docucloud.backend.documents.model.DocumentCategoryId;
import com.docucloud.backend.documents.repository.CategoryRepository;
import com.docucloud.backend.documents.repository.DocumentCategoryRepository;
import com.docucloud.backend.documents.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
            // 1. Recuperar el documento al inicio para usarlo en todo el proceso
            Document doc = documentRepo.findById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + documentId));

            // 2. Llama microservicio Python
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

            // 3. Busca categoría del usuario o la crea
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

            // 4. Construye clave compuesta y verifica existencia
            DocumentCategoryId dcId = new DocumentCategoryId(documentId, category.getId());

            if (docCatRepo.existsById(dcId)) {
                log.info("⚠️ Ya clasificado: docId={} catId={}", documentId, category.getId());
                return;
            }

            // 5. Guarda clasificación AI en document_categories
            // IMPORTANTE: Pasamos 'doc' y 'category' para que @MapsId no falle
            DocumentCategory dc = DocumentCategory.builder()
                    .id(dcId)
                    .document(doc)
                    .category(category)
                    .isAutomaticallyAssigned(true)
                    .confidenceScore(BigDecimal.valueOf(confidence))
                    .build();

            docCatRepo.save(dc);

            // 6. Sincroniza la columna category_id en documents (reutilizando 'doc')
            doc.setCategoryId(category.getId());
            documentRepo.save(doc);

            log.info("✅ AI guardado: doc={} → cat='{}' conf={}", documentId, categoryName, confidence);

        } catch (Exception e) {
            log.warn("⚠️ Classifier falló para '{}': {}", fileName, e.getMessage());
        }
    }

    @Async
    public void classifyAndAssignAsync(Long documentId, String fileName, Long userId) {
        classifyAndAssign(documentId, fileName, userId);
    }

    private record ClassifyResponse(String category, double confidence) {}
}