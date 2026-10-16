CREATE TABLE report_template (
    id VARCHAR(36) PRIMARY KEY,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE report_template_revision (
    id VARCHAR(36) PRIMARY KEY,
    template_id VARCHAR(36) NOT NULL REFERENCES report_template(id),
    revision INTEGER NOT NULL CHECK (revision > 0),
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    definition_json TEXT NOT NULL,
    definition_hash VARCHAR(64) NOT NULL,
    UNIQUE (template_id, revision)
);
CREATE TABLE generated_report (
    id VARCHAR(36) PRIMARY KEY,
    template_revision_id VARCHAR(36) NOT NULL REFERENCES report_template_revision(id),
    analysis_id VARCHAR(36) NOT NULL REFERENCES survey_general_analysis(id),
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    content_json TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL
);
CREATE TABLE generated_report_cross (
    report_id VARCHAR(36) NOT NULL REFERENCES generated_report(id),
    cross_id VARCHAR(36) NOT NULL REFERENCES survey_general_cross(id),
    position INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (report_id, position),
    UNIQUE (report_id, cross_id)
);
