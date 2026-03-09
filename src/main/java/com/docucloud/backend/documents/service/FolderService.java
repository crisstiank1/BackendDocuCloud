package com.docucloud.backend.documents.service;

import com.docucloud.backend.documents.dto.request.CreateFolderRequest;
import com.docucloud.backend.documents.dto.request.RenameFolderRequest;
import com.docucloud.backend.documents.dto.response.DocumentResponse;
import com.docucloud.backend.documents.dto.response.FolderResponse;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.Folder;
import com.docucloud.backend.documents.repository.DocumentRepository;
import com.docucloud.backend.documents.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;

    // ─── 1. Crear carpeta (RAÍZ O SUBCARPETA) ─────────────────────────────────
    public FolderResponse createFolder(Long userId, CreateFolderRequest request) {

        Folder folder = new Folder();
        folder.setOwnerUserId(userId);
        folder.setName(request.getName());

        // ← NUEVO: Soporte subcarpetas CU-10a-02
        if (request.getParentId() != null) {
            // Verificar padre existe y pertenece al usuario
            Folder parent = folderRepository.findByIdAndOwnerUserId(request.getParentId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Carpeta padre no encontrada"));

            // ← NUEVO: Profundidad máxima 5 niveles (RN-30)
            if (calculateDepth(parent) >= 5) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No se pueden crear carpetas con más de 5 niveles de profundidad");
            }

            // ← NUEVO: Nombres únicos POR HERMANOS (no global)
            if (folderRepository.existsByOwnerUserIdAndParentIdAndName(userId, request.getParentId(), request.getName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una subcarpeta con ese nombre en esta carpeta");
            }

            folder.setParentId(request.getParentId());
            log.info("📁 Subcarpeta created - user={} folderId={} name={} parentId={}",
                    userId, folder.getId(), request.getName(), request.getParentId());
        } else {
            // Raíz: nombres únicos globales (comportamiento anterior)
            if (folderRepository.existsByOwnerUserIdAndParentIdIsNullAndName(userId, request.getName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una carpeta raíz con ese nombre");
            }
            log.info("📁 Carpeta raíz created - user={} folderId={} name={}", userId, folder.getId(), request.getName());
        }

        folder = folderRepository.save(folder);
        return FolderResponse.from(folder);
    }

    // ─── 2. Listar carpetas (SOLO RAÍZ) ──────────────────────────────────────
    @Transactional(readOnly = true)
    public List<FolderResponse> listFolders(Long userId) {
        return folderRepository.findByOwnerUserIdAndParentIdIsNullOrderByNameAsc(userId)
                .stream()
                .map(FolderResponse::from)
                .collect(Collectors.toList());
    }

    // ─── 3. Documentos de una carpeta ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<DocumentResponse> getDocumentsByFolder(Long userId, Long folderId, Pageable pageable) {
        folderRepository.findByIdAndOwnerUserId(folderId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Carpeta no encontrada"));

        return documentRepository
                .findByOwnerUserIdAndFolderIdAndDeletedAtIsNull(userId, folderId, pageable)
                .map(DocumentResponse::from);
    }

    // ─── 4. Mover documento a carpeta ─────────────────────────────────────────
    public DocumentResponse moveToFolder(Long userId, Long docId, Long folderId) {
        Document doc = documentRepository
                .findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no encontrado"));

        folderRepository.findByIdAndOwnerUserId(folderId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Carpeta no encontrada"));

        doc.setFolderId(folderId);
        documentRepository.save(doc);

        log.info("📂 Doc moved - user={} doc={} folder={}", userId, docId, folderId);
        return DocumentResponse.from(doc);
    }

    // ─── 5. Quitar documento de carpeta ───────────────────────────────────────
    public DocumentResponse removeFromFolder(Long userId, Long docId) {
        Document doc = documentRepository
                .findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no encontrado"));

        doc.setFolderId(null);
        documentRepository.save(doc);

        log.info("📤 Doc removed from folder - user={} doc={}", userId, docId);
        return DocumentResponse.from(doc);
    }

    // ─── 6. Renombrar carpeta (por nivel) ─────────────────────────────────────
    public FolderResponse renameFolder(Long userId, Long folderId, RenameFolderRequest request) {
        Folder folder = folderRepository.findByIdAndOwnerUserId(folderId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Carpeta no encontrada"));

        // ← NUEVO: validar por nivel (no global)
        if (folder.getParentId() == null) {
            // Raíz
            if (folderRepository.existsByOwnerUserIdAndParentIdIsNullAndName(userId, request.getName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una carpeta raíz con ese nombre");
            }
        } else {
            // Subcarpeta
            if (folderRepository.existsByOwnerUserIdAndParentIdAndName(userId, folder.getParentId(), request.getName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una subcarpeta con ese nombre en esta carpeta");
            }
        }

        folder.setName(request.getName());
        folderRepository.save(folder);

        log.info("✏️ Folder renamed - user={} folderId={} newName={}", userId, folderId, request.getName());
        return FolderResponse.from(folder);
    }

    // ─── 7. Eliminar carpeta (desasocia docs) ─────────────────────────────────
    public void deleteFolder(Long userId, Long folderId) {
        Folder folder = folderRepository.findByIdAndOwnerUserId(folderId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Carpeta no encontrada"));

        // Desasociar documentos (no eliminarlos)
        documentRepository.findByOwnerUserIdAndFolderIdAndDeletedAtIsNull(userId, folderId, Pageable.unpaged())
                .forEach(doc -> {
                    doc.setFolderId(null);
                    documentRepository.save(doc);
                });

        folderRepository.delete(folder);
        log.info("🗑️ Folder deleted - user={} folderId={}", userId, folderId);
    }

    // ─── 8. UTIL: Calcular profundidad (CU-10a-02) ────────────────────────────
    private int calculateDepth(Folder folder) {
        int depth = 0;
        Long parentId = folder.getParentId();
        while (parentId != null) {
            Folder parent = folderRepository.findById(parentId).orElse(null);
            if (parent == null) break;
            depth++;
            parentId = parent.getParentId();
        }
        return depth;
    }
}
