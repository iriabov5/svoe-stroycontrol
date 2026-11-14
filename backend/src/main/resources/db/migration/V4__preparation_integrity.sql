ALTER TABLE mortgage_import_requests ADD COLUMN created_at timestamptz NOT NULL DEFAULT clock_timestamp();
ALTER TABLE construction_requests ADD CONSTRAINT request_submission_complete CHECK (
 state='DRAFT' OR (
 submitted_at IS NOT NULL AND coalesce(length(btrim(description)),0)>0 AND coalesce(length(btrim(completion_scope)),0)>0 AND desired_budget IS NOT NULL AND floors IS NOT NULL
 AND desired_start IS NOT NULL AND desired_finish IS NOT NULL AND financing_source IS NOT NULL
 AND (coalesce(length(btrim(technology)),0)>0 OR technology_recommendation) AND (coalesce(length(btrim(materials)),0)>0 OR materials_recommendation)
 AND (area_m2 IS NOT NULL OR (length_m IS NOT NULL AND width_m IS NOT NULL))
 AND ((length_m IS NULL)=(width_m IS NULL))));
ALTER TABLE construction_proposals ADD CONSTRAINT proposal_sent_complete CHECK(state='DRAFT' OR (submitted_at IS NOT NULL AND length(btrim(estimate_text))>0 AND total_price IS NOT NULL));
ALTER TABLE construction_contracts ADD CONSTRAINT contract_sent_complete CHECK(state='DRAFT' OR (submitted_at IS NOT NULL AND length(btrim(contract_text))>0));
