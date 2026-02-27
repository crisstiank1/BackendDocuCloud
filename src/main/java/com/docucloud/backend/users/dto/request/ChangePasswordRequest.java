package com.docucloud.backend.users.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        String currentPassword, // null si es usuario GOOGLE

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, message = "Mínimo 8 caracteres")
        String newPassword
) {}
