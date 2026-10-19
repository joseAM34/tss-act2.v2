CREATE TABLE question_text_dictionary (
    id VARCHAR(36) PRIMARY KEY,
    version_id VARCHAR(36) NOT NULL REFERENCES survey_version(id),
    question_key VARCHAR(64) NOT NULL,
    definition_hash VARCHAR(64) NOT NULL,
    revision INTEGER NOT NULL,
    rules_text TEXT NOT NULL,
    rules_hash VARCHAR(64) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(version_id, question_key, definition_hash, revision)
);
