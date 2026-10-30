\if :{?user_count}
\else
\set user_count 1000
\endif
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO users (name, email, password, role)
SELECT 'Load Test User ' || n,
       'load-user-' || lpad(n::text, 9, '0') || '@shopsphere.test',
       password_hash.value,
       'USER'
FROM generate_series(1, :'user_count'::bigint) AS n
CROSS JOIN (SELECT crypt('LoadTest-Only-2026!', gen_salt('bf', 10)) AS value) password_hash
ON CONFLICT (email) DO NOTHING;
