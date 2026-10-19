CREATE TABLE report_template_management_event (
    id VARCHAR(36) PRIMARY KEY,
    template_id VARCHAR(80) NOT NULL,
    action VARCHAR(20) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    snapshot_json TEXT NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL
);
CREATE TABLE hidden_legacy_report_template (
    template_id VARCHAR(36) PRIMARY KEY,
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
