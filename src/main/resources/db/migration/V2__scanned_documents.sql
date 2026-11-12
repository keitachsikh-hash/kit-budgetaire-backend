-- Copies manuscrites (photos ou PDF de cahiers de comptes) archivées par l'utilisateur.
CREATE TABLE scanned_document (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    title            VARCHAR(120),
    note             VARCHAR(500),
    -- Miniature en data URL (jusqu'à 200 000 caractères) : TEXT (64 Ko) serait trop petit.
    thumbnail        MEDIUMTEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ARCHIVED',
    extracted_count  INT          NOT NULL DEFAULT 0,
    imported_count   INT          NOT NULL DEFAULT 0,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_scanned_document_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX idx_scanned_document_user ON scanned_document (user_id, created_at);

-- Contenu du fichier, séparé pour ne pas le charger dans les listes (jusqu'à 15 Mo).
CREATE TABLE scanned_document_file (
    document_id  BIGINT   NOT NULL PRIMARY KEY,
    data         LONGBLOB NOT NULL,
    CONSTRAINT fk_scanned_file_document FOREIGN KEY (document_id) REFERENCES scanned_document (id) ON DELETE CASCADE
);
