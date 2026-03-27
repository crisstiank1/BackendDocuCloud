package com.docucloud.backend.documents.validation;

import java.util.Set;

public final class AllowedFileTypes {

    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".docx", ".txt",
            ".jpg", ".jpeg", ".png",
            ".xml", ".csv",
            ".xlsx", ".xls", ".xlsm",
            ".pptx", ".ppt",
            ".odt", ".ods", ".odp",
            ".tiff", ".tif", ".heic",
            ".json", ".rtf", ".md", ".log",
            ".ai", ".psd", ".indd", ".eps", ".svg"
    );

    public static boolean isAllowed(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }
}