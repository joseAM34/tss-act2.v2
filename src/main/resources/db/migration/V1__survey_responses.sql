CREATE TABLE survey_response (
    id VARCHAR(36) PRIMARY KEY,
    submission_token VARCHAR(36) NOT NULL UNIQUE,
    survey_slug VARCHAR(32) NOT NULL CHECK (survey_slug IN ('titulados', 'empleadores')),
    instrument_version VARCHAR(64) NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consent BOOLEAN NOT NULL CHECK (consent = TRUE)
);

CREATE TABLE survey_answer (
    response_id VARCHAR(36) NOT NULL REFERENCES survey_response(id),
    question_id VARCHAR(64) NOT NULL,
    question_index INTEGER NOT NULL CHECK (question_index >= 0),
    question_label TEXT NOT NULL,
    item_index INTEGER NOT NULL CHECK (item_index >= 0),
    answer_value TEXT NOT NULL,
    display_value TEXT NOT NULL,
    PRIMARY KEY (response_id, question_id, item_index)
);

CREATE INDEX idx_survey_response_submitted_at ON survey_response(submitted_at);
CREATE INDEX idx_survey_response_slug ON survey_response(survey_slug);
