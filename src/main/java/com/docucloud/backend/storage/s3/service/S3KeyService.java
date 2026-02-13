package com.docucloud.backend.storage.s3.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Service
public class S3KeyService {

    public String buildDocumentKey(Long ownerUserId, String originalFileName) {
        return "users/" + ownerUserId + "/" + UUID.randomUUID() + "-" + sanitizeFileName(originalFileName);
    }

    private String sanitizeFileName(String input) {
        if (input == null || input.isBlank()) return "file";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        normalized = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-")
                .replaceAll("-{2,}", "-");
        return normalized.length() > 180 ? normalized.substring(normalized.length() - 180) : normalized;
    }
}
