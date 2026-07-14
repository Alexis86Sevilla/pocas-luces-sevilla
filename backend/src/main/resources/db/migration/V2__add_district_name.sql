-- Add district grouping column to Enel outages.
ALTER TABLE enel_outages ADD COLUMN district_name VARCHAR(100);
CREATE INDEX idx_enel_outage_district ON enel_outages (district_name);

-- Remove obsolete category column from neighborhoods.
ALTER TABLE neighborhoods DROP COLUMN category;
