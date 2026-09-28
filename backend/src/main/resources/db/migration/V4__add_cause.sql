-- Persist Endesa's own outage cause (feed field des_cause_es, e.g. "Avería" or
-- "Trabajos programados") instead of inferring "Programado" from service_type.
ALTER TABLE enel_outages ADD COLUMN cause VARCHAR(255);
