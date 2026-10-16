-- Editor de borradores. No modifica capturas, instrumentos ni respuestas existentes.
CREATE TABLE survey (
    id VARCHAR(36) PRIMARY KEY,
    study_id VARCHAR(64) NOT NULL REFERENCES survey_study(id),
    slug VARCHAR(32) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor VARCHAR(100) NOT NULL
);
CREATE TABLE survey_version (
    id VARCHAR(36) PRIMARY KEY,
    survey_id VARCHAR(36) NOT NULL REFERENCES survey(id),
    version_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status = 'BORRADOR'),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    source_slug VARCHAR(32),
    source_version VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (survey_id, version_code)
);
CREATE TABLE survey_section (
    version_id VARCHAR(36) NOT NULL REFERENCES survey_version(id),
    section_id VARCHAR(36) NOT NULL,
    title VARCHAR(200) NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (version_id, section_id)
);
CREATE TABLE survey_question (
    version_id VARCHAR(36) NOT NULL,
    question_id VARCHAR(64) NOT NULL,
    section_id VARCHAR(36) NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    label TEXT NOT NULL,
    type VARCHAR(16) NOT NULL CHECK (type IN ('radio', 'checkbox', 'select', 'text', 'textarea', 'number', 'email', 'tel', 'url')),
    required BOOLEAN NOT NULL,
    hint TEXT NOT NULL,
    min_selections INTEGER NOT NULL,
    max_selections INTEGER NOT NULL,
    min_value INTEGER,
    max_value INTEGER,
    depends_on VARCHAR(64),
    depends_value TEXT,
    PRIMARY KEY (version_id, question_id),
    FOREIGN KEY (version_id, section_id) REFERENCES survey_section(version_id, section_id)
);
CREATE TABLE survey_question_option (
    version_id VARCHAR(36) NOT NULL,
    question_id VARCHAR(64) NOT NULL,
    option_code VARCHAR(100) NOT NULL,
    label TEXT NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (version_id, question_id, option_code),
    FOREIGN KEY (version_id, question_id) REFERENCES survey_question(version_id, question_id)
);
CREATE TABLE survey_definition_event (
    id VARCHAR(36) PRIMARY KEY,
    version_id VARCHAR(36) NOT NULL REFERENCES survey_version(id),
    revision BIGINT NOT NULL,
    operation VARCHAR(32) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    definition_json TEXT NOT NULL,
    definition_hash VARCHAR(64) NOT NULL,
    UNIQUE (version_id, revision)
);
