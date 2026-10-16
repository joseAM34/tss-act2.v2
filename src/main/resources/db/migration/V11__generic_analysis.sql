CREATE TABLE survey_general_analysis (
    id VARCHAR(36) PRIMARY KEY,
    preparation_id VARCHAR(36) NOT NULL REFERENCES survey_preparation_run(id),
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    result_json TEXT NOT NULL,
    result_hash VARCHAR(64) NOT NULL
);
