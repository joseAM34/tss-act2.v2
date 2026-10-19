-- La ausencia de una columna de consentimiento en una recepción histórica
-- no equivale a aceptación ni a rechazo del participante. El recibo administrativo
-- se conserva con la revisión y evidencia de importación, separado de esta columna.
ALTER TABLE survey_response ALTER COLUMN consent DROP NOT NULL;
