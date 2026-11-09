CREATE TABLE shared_numeric_dictionary (
 id VARCHAR(36) PRIMARY KEY,
 name VARCHAR(120) NOT NULL,
 unit VARCHAR(20) NOT NULL,
 actor VARCHAR(100) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE shared_numeric_dictionary_revision (
 dictionary_id VARCHAR(36) NOT NULL REFERENCES shared_numeric_dictionary(id),
 revision INTEGER NOT NULL,
 rules_json TEXT NOT NULL,
 rules_hash VARCHAR(64) NOT NULL,
 actor VARCHAR(100) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 PRIMARY KEY(dictionary_id,revision)
);
