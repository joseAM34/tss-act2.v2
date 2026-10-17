CREATE TABLE report_presentation_template (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    definition_json TEXT NOT NULL,
    definition_hash VARCHAR(64) NOT NULL
);
