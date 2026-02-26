package com.docucloud.backend.documents.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateFolderRequest {

    @NotBlank(message = "El nombre no puede estar vacío")
    @Size(max = 100, message = "Nombre máximo 100 caracteres")
    private String name;
}
