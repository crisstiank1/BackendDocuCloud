package com.docucloud.backend.documents.repository;

import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DocumentSpecification {

    private DocumentSpecification() {
        // Clase utilitaria, no instanciar
    }

    public static Specification<Document> search(
            Long ownerId,
            String nameQuery,
            String mimeType,
            DocumentStatus status,
            Instant fromDate,
            Instant toDate) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Evita duplicados en count query de paginación
            if (query != null) {
                query.distinct(true);
            }

            // Siempre: owner + no eliminado
            predicates.add(cb.equal(root.get("ownerUserId"), ownerId));
            predicates.add(cb.isNull(root.get("deletedAt")));

            // Nombre (LIKE insensible a mayúsculas)
            if (nameQuery != null && !nameQuery.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("fileName")),
                        "%" + nameQuery.toLowerCase() + "%"
                ));
            }

            // Tipo MIME exacto
            if (mimeType != null && !mimeType.isBlank()) {
                predicates.add(cb.equal(root.get("mimeType"), mimeType));
            }

            // Status (enum)
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // Rango de fechas
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
