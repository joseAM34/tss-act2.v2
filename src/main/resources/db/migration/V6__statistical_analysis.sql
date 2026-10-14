CREATE TABLE statistical_analysis (
    id VARCHAR(36) PRIMARY KEY,
    source_run_id VARCHAR(36) NOT NULL REFERENCES wrangling_run(id),
    source_logical_hash VARCHAR(64) NOT NULL,
    protocol_version VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor VARCHAR(100) NOT NULL,
    logical_hash VARCHAR(64) NOT NULL,
    bundle_hash VARCHAR(64) NOT NULL,
    bundle_json TEXT NOT NULL
);
CREATE INDEX statistical_analysis_source_idx ON statistical_analysis(source_run_id, created_at);
