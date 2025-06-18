CREATE TABLE translation_task
(
    id              BIGINT PRIMARY KEY,
    status          TEXT        NOT NULL,
    source_audio_id BIGINT      NOT NULL REFERENCES audio (id),
    original_text   TEXT,
    stt_text        TEXT,
    target_language jsonb       NOT NULL,
    result_file     BIGINT,
    error_message   TEXT,
    create_time     timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finish_time     timestamptz
);

CREATE TABLE audio
(
    id           BIGINT PRIMARY KEY,
    name         TEXT        NOT NULL,
    path         TEXT        NOT NULL UNIQUE,
    size         BIGINT      NOT NULL,
    extension    TEXT        NOT NULL,
    content_type TEXT        NOT NULL,
    create_time  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);