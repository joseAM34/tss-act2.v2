CREATE TABLE numeric_dictionary_revision (
 context_key VARCHAR(64) NOT NULL,
 revision INTEGER NOT NULL,
 survey_id VARCHAR(36) NOT NULL REFERENCES survey(id),
 question_key VARCHAR(64) NOT NULL,
 rules_json TEXT NOT NULL,
 rules_hash VARCHAR(64) NOT NULL,
 actor VARCHAR(100) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 PRIMARY KEY(context_key,revision)
);
