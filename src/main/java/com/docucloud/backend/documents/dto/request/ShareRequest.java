package com.docucloud.backend.documents.dto.request;

import com.docucloud.backend.documents.model.Permission;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShareRequest {
    @NotNull
    private Permission permission;
    private Integer expiresDays;
    private String password;
}
