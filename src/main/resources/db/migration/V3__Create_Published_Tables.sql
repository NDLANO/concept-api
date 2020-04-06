ALTER TABLE conceptdata ADD COLUMN revision integer not null default 1;

CREATE TABLE publishedconceptdata (
  id BIGSERIAL PRIMARY KEY,
  external_id TEXT[],
  document JSONB,
  listing_id integer null,
  revision integer not null default 1
);
