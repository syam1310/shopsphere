INSERT INTO inventory (product_id, quantity, reserved_quantity, created_at, updated_at)
SELECT
  product_id,
  20 + (series % 31),
  0,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM generate_series(1, 1000) AS series
CROSS JOIN LATERAL (
  SELECT 2001 + series AS product_id
) generated
ON CONFLICT (product_id) DO NOTHING;
