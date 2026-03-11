package com.docucloud.backend.documents.repository;

import com.docucloud.backend.documents.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {

    // 1. SEGURIDAD: Buscar carpeta asegurando que pertenezca al usuario
    Optional<Folder> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    // 2. NAVEGACIÓN RAÍZ: Carpetas que no tienen padre (Nivel 0)
    List<Folder> findByOwnerUserIdAndParentIdIsNullOrderByNameAsc(Long ownerUserId);

    // 3. NAVEGACIÓN SUBDIRECTORIOS: Carpetas dentro de otra carpeta
    List<Folder> findByOwnerUserIdAndParentIdOrderByNameAsc(Long ownerUserId, Long parentId);

    // 4. VALIDACIÓN DE NOMBRES (POR NIVEL):
    // Para raíz
    boolean existsByOwnerUserIdAndParentIdIsNullAndName(Long ownerUserId, String name);
    // Para subcarpetas (hermanos)
    boolean existsByOwnerUserIdAndParentIdAndName(Long ownerUserId, Long parentId, String name);

    // 5. BÚSQUEDA POR RUTA: Útil para la funcionalidad de 'fullPath' de tu amigo
    Optional<Folder> findByOwnerUserIdAndFullPath(Long ownerUserId, String fullPath);

    // 6. UTILIDAD RECURSIVA: Buscar todas las carpetas que empiecen por una ruta
    // (Útil para mover o borrar carpetas con todo su contenido)
    List<Folder> findByOwnerUserIdAndFullPathStartingWith(Long ownerUserId, String pathPrefix);
}