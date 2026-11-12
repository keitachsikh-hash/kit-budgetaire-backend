package com.daaradji.budget.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "scanned_document_file")
@Getter
@Setter
@NoArgsConstructor
public class ScannedDocumentFile {

    @Id
    private Long documentId;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

    public ScannedDocumentFile(Long documentId, byte[] data) {
        this.documentId = documentId;
        this.data = data;
    }
}
