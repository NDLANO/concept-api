ALTER TABLE conceptdata ADD COLUMN IF NOT EXISTS revision integer not null default 1;

-- Clones conceptdata into publishedconceptdata
CREATE TABLE publishedconceptdata as (select * from conceptdata);
