CREATE TABLE universal_dictionary_recovery (
    id VARCHAR(36) PRIMARY KEY,
    source_revision INTEGER NOT NULL,
    recovered_revision INTEGER NOT NULL,
    original_rules TEXT NOT NULL,
    stored_hash VARCHAR(64) NOT NULL,
    observed_hash VARCHAR(64) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
