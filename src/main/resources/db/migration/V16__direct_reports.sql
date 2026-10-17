-- A direct report freezes its presentation without requiring a reusable template.
-- Existing template references and foreign keys remain intact.
ALTER TABLE generated_report ALTER COLUMN template_revision_id DROP NOT NULL;
