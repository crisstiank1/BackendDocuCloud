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

    // ─── 1. Crear carpeta ─────────────────────────────────────────────────────
    public FolderResponse createFolder(Long userId, CreateFolderRequest request) {

        if (folderRepository.existsByOwnerUserIdAndName(userId, request.getName()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una carpeta con ese nombre");

        Folder folder = new Folder();
        folder.setOwnerUserId(userId);
        folder.setName(request.getName());

        folder = folderRepository.save(folder);

        log.info("📁 Folder created - user={} folderId={} name={}", userId, folder.getId(), folder.getName());
        return FolderResponse.from(folder);
    }

    // ─── 2. Listar carpetas ───────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<FolderResponse> listFolders(Long userId) {
        return folderRepository.findByOwnerUserIdOrderByNameAsc(userId)
                .stream()
                .map(FolderResponse::from)
                .collect(Collectors.toList());
    }

    // ─── 3. Documentos de una carpeta ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<DocumentResponse> getDocumentsByFolder(Long userId, Long folderId, Pageable pageable) {

        // Verificar que la carpeta existe y pertenece al usuario
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

    // ─── 6. Renombrar carpeta ─────────────────────────────────────────────────
    public FolderResponse renameFolder(Long userId, Long folderId, RenameFolderRequest request) {

        Folder folder = folderRepository.findByIdAndOwnerUserId(folderId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Carpeta no encontrada"));

        if (folderRepository.existsByOwnerUserIdAndName(userId, request.getName()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una carpeta con ese nombre");

        folder.setName(request.getName());
        folderRepository.save(folder);

        log.info("✏️ Folder renamed - user={} folderId={} newName={}", userId, folderId, request.getName());
        return FolderResponse.from(folder);
    }

    // ─── 7. Eliminar carpeta ──────────────────────────────────────────────────
    public void deleteFolder(Long userId, Long folderId) {

        Folder folder = folderRepository.findByIdAndOwnerUserId(folderId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Carpeta no encontrada"));

        // Desasociar documentos de la carpeta antes de eliminar
        documentRepository.findByOwnerUserIdAndFolderIdAndDeletedAtIsNull(userId, folderId, Pageable.unpaged())
                .forEach(doc -> {
                    doc.setFolderId(null);
                    documentRepository.save(doc);
                });

        folderRepository.delete(folder);

        log.info("🗑️ Folder deleted - user={} folderId={}", userId, folderId);
    }
}
