CREATE TABLE scoped_text_dictionary_revision (
    context_key VARCHAR(64) NOT NULL,
    revision INTEGER NOT NULL,
    survey_id VARCHAR(36) NOT NULL REFERENCES survey(id),
    question_key VARCHAR(64) NOT NULL,
    definition_hash VARCHAR(64) NOT NULL,
    dictionary_scope VARCHAR(10) NOT NULL CHECK (dictionary_scope IN ('GLOBAL','PRIVATE')),
    rules_text TEXT NOT NULL,
    rules_hash VARCHAR(64) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(context_key,revision)
);
CREATE INDEX scoped_dictionary_survey ON scoped_text_dictionary_revision(survey_id);
