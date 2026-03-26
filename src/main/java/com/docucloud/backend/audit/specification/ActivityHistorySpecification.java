package com.docucloud.backend.audit.specification;

import com.docucloud.backend.audit.model.ActivityHistory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ActivityHistorySpecification {

    public static Specification<ActivityHistory> filter(
            Long userId, String action, String resourceType,
            Instant from, Instant to) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null)
                predicates.add(cb.equal(root.get("userId"), userId));

            if (action != null)
                predicates.add(cb.like(                          // ✅ consistente con resourceType
                        cb.upper(root.get("action")),
                        "%" + action.toUpperCase() + "%"
                ));

            if (resourceType != null)
                predicates.add(cb.equal(cb.upper(root.get("resourceType")), resourceType));

            if (from != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));

            if (to != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}