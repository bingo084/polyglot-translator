CREATE TABLE translation_task
(
    id              BIGINT PRIMARY KEY,
    status          TEXT             NOT NULL,
    target_language jsonb            NOT NULL,
    result_path     TEXT,
    progress        DOUBLE PRECISION NOT NULL DEFAULT 0,
    error_message   TEXT,
    retry_count     INT              NOT NULL DEFAULT 0,
    create_time     timestamptz      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     timestamptz      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finish_time     timestamptz
);

CREATE TABLE audio
(
    id            BIGINT PRIMARY KEY,
    name          TEXT        NOT NULL,
    path          TEXT        NOT NULL UNIQUE,
    size          BIGINT      NOT NULL,
    extension     TEXT        NOT NULL,
    content_type  TEXT        NOT NULL,
    original_text TEXT,
    stt_text      TEXT,
    wer           DOUBLE PRECISION,
    create_time   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN audio.wer IS 'Word Error Rate (WER) for the STT transcription.';

CREATE TABLE translation_task_audio_mapping
(
    translation_task_id BIGINT NOT NULL REFERENCES translation_task (id),
    audio_id            BIGINT NOT NULL REFERENCES audio (id),
    PRIMARY KEY (translation_task_id, audio_id)
);
