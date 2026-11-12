package com.daaradji.budget.document;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScannedDocumentRepository extends JpaRepository<ScannedDocument, Long> {

    Page<ScannedDocument> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Optional<ScannedDocument> findByIdAndUserId(Long id, Long userId);
}
