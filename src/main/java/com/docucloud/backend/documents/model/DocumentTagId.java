package com.docucloud.backend.documents.model;

import java.io.Serializable;
import java.util.Objects;

public class DocumentTagId implements Serializable {
    private Long document; // Coincide con el nombre del atributo en DocumentTag
    private Long tag;      // Coincide con el nombre del atributo en DocumentTag

    public DocumentTagId() {}

    public DocumentTagId(Long document, Long tag) {
        this.document = document;
        this.tag = tag;
    }

    // Getters, Setters, equals y hashCode basados en Long
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentTagId that = (DocumentTagId) o;
        return Objects.equals(document, that.document) && Objects.equals(tag, that.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(document, tag);
    }
}