package com.daaradji.budget.document;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.daaradji.budget.account.Account;
import com.daaradji.budget.account.AccountRepository;
import com.daaradji.budget.category.Category;
import com.daaradji.budget.category.CategoryRepository;
import com.daaradji.budget.category.CategoryType;
import com.daaradji.budget.common.BusinessException;
import com.daaradji.budget.common.NotFoundException;
import com.daaradji.budget.document.DocumentDtos.DocumentResponse;
import com.daaradji.budget.document.DocumentDtos.Extraction;
import com.daaradji.budget.document.DocumentDtos.UpdateRequest;
import com.daaradji.budget.transaction.TransactionDtos.PageResponse;

@Service
public class DocumentService {

    static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf");
    private static final long MAX_BYTES = 15L * 1024 * 1024;
    private static final int MAX_THUMBNAIL_CHARS = 200_000;

    private final ScannedDocumentRepository documents;
    private final ScannedDocumentFileRepository files;
    private final CategoryRepository categories;
    private final AccountRepository accounts;
    private final HandwritingExtractor extractor;
    private final TransactionTemplate tx;

    public DocumentService(ScannedDocumentRepository documents, ScannedDocumentFileRepository files,
            CategoryRepository categories, AccountRepository accounts, HandwritingExtractor extractor,
            TransactionTemplate tx) {
        this.documents = documents;
        this.files = files;
        this.categories = categories;
        this.accounts = accounts;
        this.extractor = extractor;
        this.tx = tx;
    }

    public PageResponse<DocumentResponse> list(Long userId, int page, int size) {
        return tx.execute(s -> PageResponse.of(documents
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50)))
                .map(DocumentResponse::of)));
    }

    public DocumentResponse upload(Long userId, MultipartFile file, String title, String note, String thumbnail) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choisissez une photo ou un PDF.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BusinessException("Format non pris en charge : envoyez une photo (JPEG, PNG, WebP) ou un PDF.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("Fichier trop lourd (15 Mo maximum).");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("Le fichier n'a pas pu être lu.");
        }
        ScannedDocument d = new ScannedDocument();
        d.setUserId(userId);
        d.setFileName(trim(file.getOriginalFilename() == null ? "copie" : file.getOriginalFilename(), 255));
        d.setContentType(type);
        d.setSizeBytes(data.length);
        d.setTitle(blankToNull(trim(title, 120)));
        d.setNote(blankToNull(trim(note, 500)));
        if (thumbnail != null && thumbnail.startsWith("data:image/") && thumbnail.length() <= MAX_THUMBNAIL_CHARS) {
            d.setThumbnail(thumbnail);
        }
        return tx.execute(s -> {
            ScannedDocument saved = documents.save(d);
            files.save(new ScannedDocumentFile(saved.getId(), data));
            return DocumentResponse.of(saved);
        });
    }

    public DocumentResponse update(Long userId, Long id, UpdateRequest req) {
        return tx.execute(s -> {
            ScannedDocument d = find(userId, id);
            d.setTitle(blankToNull(req.title()));
            d.setNote(blankToNull(req.note()));
            return DocumentResponse.of(d);
        });
    }

    public StoredFile file(Long userId, Long id) {
        return tx.execute(s -> {
            ScannedDocument d = find(userId, id);
            byte[] data = files.findById(id).map(ScannedDocumentFile::getData)
                    .orElseThrow(() -> new NotFoundException("Fichier introuvable"));
            return new StoredFile(d.getFileName(), d.getContentType(), data);
        });
    }

    public void delete(Long userId, Long id) {
        tx.executeWithoutResult(s -> documents.delete(find(userId, id)));
    }

    /** Lecture par Claude : l'appel réseau se fait hors transaction (il peut durer plusieurs dizaines de secondes). */
    public Extraction extract(Long userId, Long id) {
        StoredFile f = file(userId, id);
        List<Category> cats = tx.execute(s -> categories.findByUserIdOrderByTypeAscNameAsc(userId));
        List<String> accountNames = tx.execute(s -> accounts.findByUserIdAndArchivedFalse(userId).stream()
                .map(Account::getName).toList());
        Extraction result = extractor.extract(f.data(), f.contentType(),
                names(cats, CategoryType.EXPENSE), names(cats, CategoryType.INCOME), accountNames);
        int count = result.lines() == null ? 0 : result.lines().size();
        tx.executeWithoutResult(s -> {
            ScannedDocument d = find(userId, id);
            d.setExtractedCount(count);
            if (d.getStatus() == DocumentStatus.ARCHIVED) {
                d.setStatus(DocumentStatus.EXTRACTED);
            }
        });
        return result;
    }

    public DocumentResponse markImported(Long userId, Long id, int count) {
        return tx.execute(s -> {
            ScannedDocument d = find(userId, id);
            d.setImportedCount(d.getImportedCount() + count);
            d.setStatus(DocumentStatus.IMPORTED);
            return DocumentResponse.of(d);
        });
    }

    public boolean aiEnabled() {
        return extractor.isConfigured();
    }

    private ScannedDocument find(Long userId, Long id) {
        return documents.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Copie introuvable"));
    }

    private static List<String> names(List<Category> cats, CategoryType type) {
        return cats.stream().filter(c -> c.getType() == type).map(Category::getName).toList();
    }

    private static String trim(String s, int max) {
        return s == null ? null : s.strip().substring(0, Math.min(s.strip().length(), max));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public record StoredFile(String fileName, String contentType, byte[] data) {
    }
}
