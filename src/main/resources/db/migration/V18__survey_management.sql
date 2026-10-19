-- Las encuestas recuperables son estructuras normales, administrables y archivables.
ALTER TABLE survey ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;

-- Sin FK deliberadamente: una eliminación sin datos conserva su constancia administrativa.
CREATE TABLE survey_management_event (
    id VARCHAR(36) PRIMARY KEY,
    survey_id VARCHAR(36) NOT NULL,
    survey_slug VARCHAR(32) NOT NULL,
    operation VARCHAR(16) NOT NULL CHECK (operation IN ('ARCHIVAR', 'RESTAURAR', 'ELIMINAR')),
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    state_json TEXT NOT NULL,
    state_hash VARCHAR(64) NOT NULL
);
