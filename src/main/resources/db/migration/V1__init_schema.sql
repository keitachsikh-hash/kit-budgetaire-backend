-- Schéma initial (MySQL 8+ ; InnoDB / utf8mb4 par défaut).
-- Les clés étrangères sont déclarées en contraintes de table : MySQL ignore les REFERENCES en ligne.
CREATE TABLE app_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name   VARCHAR(120) NOT NULL,
    email       VARCHAR(160) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    currency    VARCHAR(10)  NOT NULL DEFAULT 'XOF',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_app_user_email UNIQUE (email)
);

CREATE TABLE account (
    id               BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT         NOT NULL,
    name             VARCHAR(80)    NOT NULL,
    type             VARCHAR(20)    NOT NULL,
    initial_balance  DECIMAL(15, 2) NOT NULL DEFAULT 0,
    color            VARCHAR(20)    NOT NULL DEFAULT '#0d6efd',
    archived         BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_account_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX idx_account_user ON account (user_id);

CREATE TABLE category (
    id       BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id  BIGINT      NOT NULL,
    name     VARCHAR(60) NOT NULL,
    type     VARCHAR(10) NOT NULL,
    icon     VARCHAR(40) NOT NULL DEFAULT 'tag',
    color    VARCHAR(20) NOT NULL DEFAULT '#6c757d',
    CONSTRAINT uk_category_user_name_type UNIQUE (user_id, name, type),
    CONSTRAINT fk_category_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX idx_category_user ON category (user_id);

CREATE TABLE budget_transaction (
    id           BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT         NOT NULL,
    account_id   BIGINT         NOT NULL,
    category_id  BIGINT         NOT NULL,
    type         VARCHAR(10)    NOT NULL,
    amount       DECIMAL(15, 2) NOT NULL,
    op_date      DATE           NOT NULL,
    description  VARCHAR(255),
    created_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_tx_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_tx_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE,
    CONSTRAINT fk_tx_category FOREIGN KEY (category_id) REFERENCES category (id)
);
CREATE INDEX idx_tx_user_date ON budget_transaction (user_id, op_date);
CREATE INDEX idx_tx_category ON budget_transaction (category_id);

CREATE TABLE monthly_budget (
    id            BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT         NOT NULL,
    category_id   BIGINT         NOT NULL,
    period        VARCHAR(7)     NOT NULL,
    limit_amount  DECIMAL(15, 2) NOT NULL,
    CONSTRAINT uk_budget_user_cat_period UNIQUE (user_id, category_id, period),
    CONSTRAINT fk_budget_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_budget_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE CASCADE
);

CREATE TABLE saving_goal (
    id             BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT         NOT NULL,
    name           VARCHAR(80)    NOT NULL,
    target_amount  DECIMAL(15, 2) NOT NULL,
    saved_amount   DECIMAL(15, 2) NOT NULL DEFAULT 0,
    deadline       DATE,
    icon           VARCHAR(40)    NOT NULL DEFAULT 'piggy-bank',
    color          VARCHAR(20)    NOT NULL DEFAULT '#198754',
    created_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_goal_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
