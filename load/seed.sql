-- Load-test fixtures: one payee + 10 well-funded payers (spread the payer-row FOR UPDATE
-- contention). Run after the app has migrated. Idempotent so it can re-run.

INSERT INTO accounts (id, owner_type, owner_id, type, currency)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001', 'MERCHANT', 'load-payee', 'MERCHANT_PAYABLE', 'INR')
ON CONFLICT DO NOTHING;
INSERT INTO account_balances (account_id, balance)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001', 0)
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, owner_type, owner_id, type, currency)
SELECT ('aaaaaaaa-0000-0000-0000-0000000000' || lpad(g::text, 2, '0'))::uuid,
       'USER', 'load-payer-' || g, 'USER_WALLET', 'INR'
FROM generate_series(1, 10) g
ON CONFLICT DO NOTHING;
INSERT INTO account_balances (account_id, balance)
SELECT ('aaaaaaaa-0000-0000-0000-0000000000' || lpad(g::text, 2, '0'))::uuid, 1000000000000
FROM generate_series(1, 10) g
ON CONFLICT DO NOTHING;
