CREATE TABLE survey_result_cut (
    id VARCHAR(36) PRIMARY KEY,
    survey_id VARCHAR(36) NOT NULL REFERENCES survey(id),
    name VARCHAR(200) NOT NULL,
    preparation_id VARCHAR(36) NOT NULL UNIQUE REFERENCES survey_preparation_run(id),
    snapshot_json TEXT NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_result_cut_survey ON survey_result_cut(survey_id, created_at);
