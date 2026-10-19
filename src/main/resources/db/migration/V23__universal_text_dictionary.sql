CREATE TABLE universal_dictionary_state (id INTEGER PRIMARY KEY, revision INTEGER NOT NULL);
INSERT INTO universal_dictionary_state(id,revision) VALUES(1,0);
CREATE TABLE universal_dictionary_revision (
    revision INTEGER PRIMARY KEY,
    rules_text TEXT NOT NULL,
    rules_hash VARCHAR(64) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE universal_term_lookup (
    term_key VARCHAR(64) PRIMARY KEY,
    term VARCHAR(100) NOT NULL,
    payload_json TEXT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
