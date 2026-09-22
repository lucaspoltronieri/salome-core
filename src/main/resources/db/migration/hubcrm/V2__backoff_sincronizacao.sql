ALTER TABLE hub_crm_client
  ADD COLUMN next_attempt_at DATETIME NULL AFTER attempt_count,
  ADD KEY ix_hub_client_retry (sync_status, next_attempt_at);

ALTER TABLE hub_crm_quote
  ADD COLUMN next_attempt_at DATETIME NULL AFTER attempt_count,
  ADD KEY ix_hub_quote_retry (sync_status, next_attempt_at);
