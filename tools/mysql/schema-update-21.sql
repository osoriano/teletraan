-- !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
-- ALWAYS BACKUP YOUR DATA BEFORE EXECUTING THIS SCRIPT
-- !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

-- This script upgrade DB schema from version 20 to version 21

ALTER TABLE environs ADD COLUMN multi_goal tinyint(1) NOT NULL DEFAULT '0';


-- make sure to update the schema version to 21
UPDATE schema_versions SET version=21;
