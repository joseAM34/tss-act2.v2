CREATE TABLE survey_study (
    id VARCHAR(64) PRIMARY KEY,
    name TEXT NOT NULL,
    reference_year INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('BORRADOR', 'APROBADA')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO survey_study (id, name, reference_year, status, created_at)
VALUES ('umss-mercado-2026-borrador', 'Estudio de mercado de Ingeniería en Sistemas UMSS (borrador)', 2026, 'BORRADOR', CURRENT_TIMESTAMP);

CREATE TABLE instrument_snapshot (
    survey_slug VARCHAR(32) NOT NULL,
    instrument_version VARCHAR(64) NOT NULL,
    study_id VARCHAR(64) NOT NULL REFERENCES survey_study(id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('BORRADOR', 'APROBADA', 'RETIRADA')),
    definition_json TEXT NOT NULL,
    definition_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (survey_slug, instrument_version)
);

CREATE TABLE response_capture (
    response_id VARCHAR(36) PRIMARY KEY REFERENCES survey_response(id) ON DELETE CASCADE,
    study_id VARCHAR(64) NOT NULL REFERENCES survey_study(id),
    survey_slug VARCHAR(32) NOT NULL,
    instrument_version VARCHAR(64) NOT NULL,
    definition_hash VARCHAR(64) NOT NULL,
    capture_json TEXT NOT NULL,
    capture_hash VARCHAR(64) NOT NULL,
    provenance VARCHAR(32) NOT NULL CHECK (provenance = 'CAPTURA_LOGICA'),
    FOREIGN KEY (survey_slug, instrument_version) REFERENCES instrument_snapshot(survey_slug, instrument_version)
);
