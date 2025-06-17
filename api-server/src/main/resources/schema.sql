CREATE TABLE translate_task
(
    id              BIGINT PRIMARY KEY,
    status          TEXT        NOT NULL,
    source_audio    BIGINT      NOT NULL,
    original_text   TEXT,
    stt_text        TEXT,
    target_language jsonb       NOT NULL,
    result_file     BIGINT,
    error_message   TEXT,
    create_time     timestamptz NOT NULL,
    update_time     timestamptz NOT NULL,
    finish_time     timestamptz
)