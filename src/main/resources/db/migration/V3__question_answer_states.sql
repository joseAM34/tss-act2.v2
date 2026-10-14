CREATE TABLE survey_question_state (
    response_id VARCHAR(36) NOT NULL REFERENCES survey_response(id),
    question_id VARCHAR(64) NOT NULL,
    question_index INTEGER NOT NULL CHECK (question_index >= 0),
    question_label TEXT NOT NULL,
    answer_state VARCHAR(24) NOT NULL CHECK (answer_state IN ('CON_VALOR', 'SIN_RESPUESTA', 'NO_APLICA')),
    PRIMARY KEY (response_id, question_id)
);

-- Los envíos anteriores conservan sus datos originales. No se deduce la
-- aplicabilidad de preguntas históricas a partir del catálogo actual.
