package com.docucloud.backend.documents.dto.request;

import com.docucloud.backend.documents.model.Permission;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateSharePermissionRequest {

    @NotNull(message = "El permiso es obligatorio")
    private Permission permission;
}