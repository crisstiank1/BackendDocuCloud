package com.docucloud.backend.documents.repository;

import com.docucloud.backend.documents.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    // Listar carpetas del usuario
    List<Folder> findByOwnerUserIdOrderByNameAsc(Long ownerUserId);

    // Buscar carpeta por id y owner (seguridad)
    Optional<Folder> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    // Verificar nombre duplicado
    boolean existsByOwnerUserIdAndName(Long ownerUserId, String name);

    boolean existsByOwnerUserIdAndParentIdIsNullAndName(Long ownerUserId, String name);  // raíz
    boolean existsByOwnerUserIdAndParentIdAndName(Long ownerUserId, Long parentId, String name);  // subcarpeta
    List<Folder> findByOwnerUserIdAndParentIdOrderByNameAsc(Long ownerUserId, Long parentId);
    List<Folder> findByOwnerUserIdAndParentIdIsNullOrderByNameAsc(Long ownerUserId);  // solo raíz
}
