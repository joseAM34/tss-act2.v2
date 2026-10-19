-- Constancia temporal de un respaldo descargable. No contiene respuestas ni archivos.
-- Sin FK: la constancia y la auditoría sobreviven a la eliminación de la encuesta.
CREATE TABLE survey_deletion_backup (
    id VARCHAR(36) PRIMARY KEY,
    survey_id VARCHAR(36) NOT NULL,
    slug VARCHAR(32) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    state_hash VARCHAR(64) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    archive_hash VARCHAR(64) NOT NULL,
    filename VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_survey_deletion_backup_current ON survey_deletion_backup(survey_id, actor, created_at);
