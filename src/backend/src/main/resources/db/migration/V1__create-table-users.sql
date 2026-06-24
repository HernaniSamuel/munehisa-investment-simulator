CREATE TABLE users
(
    id                          UUID PRIMARY KEY,

    name                        VARCHAR(255) NOT NULL,
    email                       VARCHAR(255) NOT NULL,
    password                    VARCHAR(255) NOT NULL,

    verification_token          VARCHAR(255),
    password_reset_token        VARCHAR(255),

    verification_token_expiry   TIMESTAMP,
    reset_password_token_expiry TIMESTAMP,

    is_verified BOOLEAN default FALSE,

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_verification_token UNIQUE (verification_token),
    CONSTRAINT uk_password_reset_token UNIQUE (password_reset_token),
);