package com.docucloud.backend.documents.service;

import com.docucloud.backend.audit.annotation.Audited;
import com.docucloud.backend.audit.service.AuditService;
import com.docucloud.backend.documents.dto.request.CreateFolderRequest;
import com.docucloud.backend.documents.dto.request.RenameFolderRequest;
import com.docucloud.backend.documents.dto.response.DocumentResponse;
import com.docucloud.backend.documents.dto.response.FolderResponse;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.Folder;
import com.docucloud.backend.documents.repository.DocumentRepository;
import com.docucloud.backend.documents.repository.FolderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository   folderRepository;
    private final DocumentRepository documentRepository;
    private final AuditService       auditService;  // ✅
    private final ObjectMapper       objectMapper;  // ✅

    // ─── 1. Crear carpeta ────────────────────────────────────────────────────

    @Audited(action = "CREATE_FOLDER", resourceType = "Folder")
    public FolderResponse createFolder(Long userId, CreateFolderRequest request) {

        Folder folder = new Folder();
        folder.setOwnerUserId(userId);
        folder.setName(request.getName());

        if (request.getParentId() != null) {
            Folder parent = folderRepository.findByIdAndOwnerUserId(request.getParentId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Carpeta padre no encontrada"));

            if (calculateDepth(parent) >= 5) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No se pueden crear carpetas con más de 5 niveles de profundidad");
            }

            if (folderRepository.existsByOwnerUserIdAndParentIdAndName(
                    userId, request.getParentId(), request.getName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una subcarpeta con ese nombre en esta carpeta");
            }

            folder.setParentId(request.getParentId());
            folder.setFullPath(parent.getFullPath() + "/" + request.getName());
            log.info("📁 Subcarpeta created - user={} name={} parentId={}",
                    userId, request.getName(), request.getParentId());
        } else {
            if (folderRepository.existsByOwnerUserIdAndParentIdIsNullAndName(
                    userId, request.getName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una carpeta raíz con ese nombre");
            }
            folder.setFullPath(request.getName());
            log.info("📁 Carpeta raíz created - user={} name={}", userId, request.getName());
        }

        folder = folderRepository.save(folder);
        return FolderResponse.from(folder);
    }

    // ─── 2. Listar carpetas ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FolderResponse> listFolders(Long userId) {
        return folderRepository.findByOwnerUserIdOrderByNameAsc(userId)
                .stream()
                .map(FolderResponse::from)
                .collect(Collectors.toList());
    }

    // ─── 3. Documentos de una carpeta ────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DocumentResponse> getDocumentsByFolder(Long userId, Long folderId, Pageable pageable) {
        folderRepository.findByIdAndOwnerUserId(folderId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Carpeta no encontrada"));

        return documentRepository
                .findByOwnerUserIdAndFolderIdAndDeletedAtIsNull(userId, folderId, pageable)
                .map(DocumentResponse::from);
    }

    // ─── 4. Mover documento a carpeta ────────────────────────────────────────

    @Audited(action = "FOLDER_MOVE", resourceType = "Document", resourceIdArgIndex = 1)
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

    // ─── 5. Quitar documento de carpeta ──────────────────────────────────────

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

    // ─── 6. Renombrar carpeta ─────────────────────────────────────────────────

    @Audited(action = "RENAME_FOLDER", resourceType = "Folder", resourceIdArgIndex = 1)
    public FolderResponse renameFolder(Long userId, Long folderId, RenameFolderRequest request) {
        Folder folder = folderRepository.findByIdAndOwnerUserId(folderId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Carpeta no encontrada"));

        if (folder.getParentId() == null) {
            if (folderRepository.existsByOwnerUserIdAndParentIdIsNullAndName(
                    userId, request.getName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una carpeta raíz con ese nombre");
            }
            folder.setFullPath(request.getName());
        } else {
            if (folderRepository.existsByOwnerUserIdAndParentIdAndName(
                    userId, folder.getParentId(), request.getName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una subcarpeta con ese nombre en esta carpeta");
            }
            Folder parent = folderRepository.findById(folder.getParentId()).orElseThrow();
            folder.setFullPath(parent.getFullPath() + "/" + request.getName());
        }

        folder.setName(request.getName());
        folderRepository.save(folder);

        log.info("✏️ Folder renamed - user={} folderId={} newName={}",
                userId, folderId, request.getName());
        return FolderResponse.from(folder);
    }

    // ─── 7. Eliminar carpeta ──────────────────────────────────────────────────

    // ✅ Sin @Audited — auditamos manualmente para poder incluir el nombre
    public void deleteFolder(Long userId, Long folderId) {
        Folder folder = folderRepository.findByIdAndOwnerUserId(folderId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Carpeta no encontrada"));

        boolean success = true;
        try {
            List<Document> docs = documentRepository
                    .findByOwnerUserIdAndFolderIdAndDeletedAtIsNull(userId, folderId, Pageable.unpaged())
                    .getContent();

            docs.forEach(doc -> doc.setFolderId(null));
            documentRepository.saveAll(docs);

            folderRepository.delete(folder);
            log.info("🗑️ Folder deleted - user={} folderId={}", userId, folderId);
        } catch (Exception ex) {
            success = false;
            throw ex;
        } finally {
            // ✅ nombre disponible porque cargamos el objeto antes de borrar
            ObjectNode details = objectMapper.createObjectNode();
            details.put("name", folder.getName());
            auditService.logBusiness(userId, "DELETE_FOLDER", "Folder", folderId, success, details);
        }
    }

    // ─── 8. UTIL: Calcular profundidad ────────────────────────────────────────

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