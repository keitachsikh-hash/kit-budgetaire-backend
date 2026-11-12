package com.daaradji.budget.document;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.daaradji.budget.config.CurrentUser;
import com.daaradji.budget.document.DocumentDtos.DocumentResponse;
import com.daaradji.budget.document.DocumentDtos.Extraction;
import com.daaradji.budget.document.DocumentDtos.ImportedRequest;
import com.daaradji.budget.document.DocumentDtos.UpdateRequest;
import com.daaradji.budget.document.DocumentService.StoredFile;
import com.daaradji.budget.transaction.TransactionDtos.PageResponse;

import jakarta.validation.Valid;

/** Archives des copies manuscrites : envoi, consultation et lecture automatique des opérations. */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;
    private final CurrentUser currentUser;

    public DocumentController(DocumentService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<DocumentResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        return service.list(currentUser.id(), page, size);
    }

    @GetMapping("/ai-status")
    public Map<String, Boolean> aiStatus() {
        return Map.of("enabled", service.aiEnabled());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String thumbnail) {
        return service.upload(currentUser.id(), file, title, note, thumbnail);
    }

    @PutMapping("/{id}")
    public DocumentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        return service.update(currentUser.id(), id, req);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        StoredFile f = service.file(currentUser.id(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(f.data());
    }

    @PostMapping("/{id}/extract")
    public Extraction extract(@PathVariable Long id) {
        return service.extract(currentUser.id(), id);
    }

    @PostMapping("/{id}/imported")
    public DocumentResponse imported(@PathVariable Long id, @Valid @RequestBody ImportedRequest req) {
        return service.markImported(currentUser.id(), id, req.count());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(currentUser.id(), id);
    }
}
