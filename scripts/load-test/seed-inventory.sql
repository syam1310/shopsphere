\if :{?product_count}
\else
\set product_count 1000
\endif
INSERT INTO inventory (product_id, quantity, reserved_quantity, created_at, updated_at)
SELECT 5000001 + n, 30, 0, now(), now()
FROM generate_series(1, :'product_count'::bigint) AS n
ON CONFLICT (product_id) DO NOTHING;
