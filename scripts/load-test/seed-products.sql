\if :{?product_count}
\else
\set product_count 1000
\endif
WITH categories(category, family, base_price, category_num) AS (
  VALUES
    ('Electronics','Wireless Mouse',499.00,1),
    ('Electronics','Bluetooth Headphones',1499.00,2),
    ('Home & Kitchen','Ceramic Dinner Set',899.00,3),
    ('Home & Kitchen','Steel Water Bottle',349.00,4),
    ('Fashion','Everyday Cotton T-shirt',599.00,5),
    ('Fashion','Classic Denim Shirt',999.00,6),
    ('Books','Mindful Living Journal',299.00,7),
    ('Beauty','Daily Face Moisturizer',449.00,8),
    ('Sports & Outdoors','Yoga Mat',799.00,9),
    ('Toys & Games','Creative Puzzle Set',399.00,10),
    ('Grocery','Arabica Coffee Beans',549.00,11),
    ('Office Supplies','Hardcover Notebook',199.00,12),
    ('Automotive','Compact Phone Mount',699.00,13),
    ('Pet Supplies','Adjustable Pet Collar',249.00,14),
    ('Health & Wellness','Foam Roller',899.00,15),
    ('Garden','Ceramic Plant Pot',349.00,16),
    ('Baby & Kids','Cotton Baby Blanket',799.00,17),
    ('Footwear','Everyday Walking Sneakers',1799.00,18),
    ('Luggage & Travel','Compact Travel Organizer',499.00,19),
    ('Furniture & Decor','Cushion Cover Set',599.00,20)
), generated AS (
  SELECT 5000001 + n AS id,
    family || ' · ' || lpad(n::text, 7, '0') AS name,
    'Synthetic load-test listing ' || n || '. Category: ' || category ||
      '. Multiple shade choices are represented by shared local shade image assets.' AS description,
    (base_price + ((n % 97) * 11.25))::numeric(12,2) AS price,
    category,
    CASE category_num
      WHEN 1 THEN '/product-images/shade-01.jpg'
      WHEN 2 THEN '/product-images/shade-05.jpg'
      WHEN 5 THEN '/product-images/shade-21.jpg'
      WHEN 18 THEN '/product-images/shade-17.jpg'
      ELSE '/product-images/catalog-extra-' || lpad(((category_num % 20) + 1)::text, 2, '0') || '.jpg'
    END AS image_url
  FROM generate_series(1, :'product_count'::bigint) AS n
  JOIN categories ON categories.category_num = ((n - 1) % 20) + 1
)
INSERT INTO products (id, name, description, price, quantity, category, image_url, active, created_at, updated_at)
SELECT id, name, description, price, 30, category, image_url, TRUE, now(), now()
FROM generated
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('products', 'id'), GREATEST(5000000 + :'product_count'::bigint, (SELECT COALESCE(MAX(id), 1) FROM products)));
