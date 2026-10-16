CREATE TABLE survey_preparation_config (
    id VARCHAR(36) PRIMARY KEY,
    version_id VARCHAR(36) NOT NULL REFERENCES survey_version(id),
    revision INTEGER NOT NULL CHECK (revision > 0),
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    config_json TEXT NOT NULL,
    config_hash VARCHAR(64) NOT NULL,
    UNIQUE (version_id, revision)
);

CREATE TABLE survey_preparation_run (
    id VARCHAR(36) PRIMARY KEY,
    version_id VARCHAR(36) NOT NULL REFERENCES survey_version(id),
    config_id VARCHAR(36) NOT NULL REFERENCES survey_preparation_config(id),
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    bundle_json TEXT NOT NULL,
    bundle_hash VARCHAR(64) NOT NULL,
    logical_hash VARCHAR(64) NOT NULL
);
CREATE INDEX ix_preparation_version ON survey_preparation_run(version_id, created_at);
