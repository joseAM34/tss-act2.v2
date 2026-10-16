CREATE TABLE csv_import_batch (
    id VARCHAR(36) PRIMARY KEY,
    version_id VARCHAR(36) NOT NULL REFERENCES survey_version(id),
    file_name VARCHAR(200) NOT NULL,
    source_base64 TEXT NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    delimiter VARCHAR(10) NOT NULL,
    platform VARCHAR(40) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (version_id, source_hash)
);
CREATE TABLE csv_import_mapping (
    id VARCHAR(36) PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL REFERENCES csv_import_batch(id),
    revision INTEGER NOT NULL CHECK (revision > 0),
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    mapping_json TEXT NOT NULL,
    mapping_hash VARCHAR(64) NOT NULL,
    UNIQUE (batch_id, revision)
);
CREATE TABLE csv_import_confirmation (
    batch_id VARCHAR(36) PRIMARY KEY REFERENCES csv_import_batch(id),
    mapping_id VARCHAR(36) NOT NULL REFERENCES csv_import_mapping(id),
    preview_hash VARCHAR(64) NOT NULL,
    overlap_reviewed BOOLEAN NOT NULL,
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE csv_import_row (
    batch_id VARCHAR(36) NOT NULL REFERENCES csv_import_batch(id),
    row_number INTEGER NOT NULL CHECK (row_number >= 2),
    mapping_id VARCHAR(36) NOT NULL REFERENCES csv_import_mapping(id),
    response_id VARCHAR(36) NOT NULL UNIQUE REFERENCES survey_response(id),
    raw_row_json TEXT NOT NULL,
    raw_row_hash VARCHAR(64) NOT NULL,
    capture_hash VARCHAR(64) NOT NULL,
    evidence_json TEXT NOT NULL,
    evidence_hash VARCHAR(64) NOT NULL,
    PRIMARY KEY (batch_id, row_number)
);
