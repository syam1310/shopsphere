WITH categories(category_order, category, families, base_price) AS (
    VALUES
      (1, 'Electronics', ARRAY['Wireless Mouse','Bluetooth Headphones','USB-C Charger','Mechanical Keyboard','Portable Speaker','Webcam','Laptop Stand','Power Bank','Smart Watch','HDMI Cable']::text[], 399.00::numeric),
      (2, 'Home & Kitchen', ARRAY['Ceramic Dinner Set','Steel Water Bottle','Storage Container Set','Cotton Bath Towel','Desk Lamp','Non-stick Cookware Set','Glass Food Jar','Table Runner','Kitchen Scale','Insulated Flask']::text[], 249.00::numeric),
      (3, 'Fashion', ARRAY['Everyday Cotton T-shirt','Classic Denim Shirt','Lightweight Hoodie','Canvas Belt','Casual Scarf','Linen Kurta','Zip-up Jacket','Cotton Socks Pack','Everyday Cap','Relaxed-fit Trousers']::text[], 299.00::numeric),
      (4, 'Books', ARRAY['The Curious Reader','Everyday Cooking Guide','Mindful Living Journal','Pocket Poetry Collection','Beginner Science Book','Travel Stories','Creative Writing Workbook','Classic Short Stories','Personal Finance Guide','Nature Sketchbook']::text[], 149.00::numeric),
      (5, 'Beauty', ARRAY['Daily Face Moisturizer','Gentle Cleanser','Nourishing Hair Oil','Lip Care Balm','Reusable Makeup Pouch','Bath and Body Set','Soft Makeup Brush Set','Aloe Face Gel','Hand Cream','Wide-tooth Comb']::text[], 129.00::numeric),
      (6, 'Sports & Outdoors', ARRAY['Yoga Mat','Steel Shaker Bottle','Resistance Band Set','Training Skipping Rope','Outdoor Travel Mug','Fitness Hand Towel','Lightweight Gym Bag','Tennis Grip Pack','Camping Lantern','Cycling Water Bottle']::text[], 199.00::numeric),
      (7, 'Toys & Games', ARRAY['Wooden Building Blocks','Family Card Game','Creative Puzzle Set','Coloring Activity Kit','Mini Board Game','Soft Plush Toy','Magnetic Letter Set','Strategy Game','Outdoor Flying Disc','Kids Art Supplies']::text[], 99.00::numeric),
      (8, 'Grocery', ARRAY['Arabica Coffee Beans','Masala Chai Blend','Roasted Almond Pack','Organic Oats','Wildflower Honey','Wholegrain Pasta','Mixed Dry Fruits','Classic Spice Box','Dark Chocolate Bites','Green Tea Bags']::text[], 79.00::numeric),
      (9, 'Office Supplies', ARRAY['Hardcover Notebook','Fine-tip Pen Set','Desk Organizer','Sticky Notes Pack','Document Folder','Daily Planner','Mechanical Pencil Set','Whiteboard Marker Set','Paper Clips Box','Compact Stapler']::text[], 39.00::numeric),
      (10, 'Automotive', ARRAY['Compact Phone Mount','Microfiber Cleaning Cloths','Car Air Freshener','Emergency Torch','Seat Back Organizer','Tyre Pressure Gauge','Travel Cup Holder','Windshield Sun Shade','Car Cleaning Brush','Cable Organizer Clips']::text[], 129.00::numeric),
      (11, 'Pet Supplies', ARRAY['Adjustable Pet Collar','Stainless Pet Bowl','Rope Chew Toy','Soft Pet Brush','Travel Water Bowl','Pet Treat Jar','Reflective Leash','Cat Play Wand','Washable Pet Mat','Pet Grooming Comb']::text[], 99.00::numeric),
      (12, 'Health & Wellness', ARRAY['Digital Thermometer','Reusable Hot Pack','Pill Organizer','Sleep Eye Mask','Foam Roller','Cotton Face Masks','Posture Support Cushion','Massage Ball Set','Reusable Ice Pack','Daily Habit Journal']::text[], 89.00::numeric),
      (13, 'Garden', ARRAY['Hand Trowel Set','Watering Can','Seed Starter Tray','Garden Pruning Shears','Plant Mister','Cotton Garden Gloves','Ceramic Plant Pot','Herb Growing Kit','Garden Twine Roll','Plant Support Stakes']::text[], 119.00::numeric),
      (14, 'Baby & Kids', ARRAY['Cotton Baby Blanket','Soft Feeding Bib Set','Stacking Cups','Baby Care Organizer','Sippy Cup','Muslin Swaddle Cloth','Kids Lunch Box','Soft Teething Ring','Baby Bath Towel','Nursery Storage Basket']::text[], 149.00::numeric),
      (15, 'Footwear', ARRAY['Everyday Walking Sneakers','Canvas Slip-on Shoes','Soft House Slippers','Lightweight Sandals','Cotton Ankle Socks','Classic Loafers','Outdoor Trail Shoes','Adjustable Flip-flops','Running Socks Pack','Casual Lace-up Shoes']::text[], 299.00::numeric),
      (16, 'Jewelry & Accessories', ARRAY['Minimal Pendant Necklace','Classic Hoop Earrings','Woven Bracelet','Everyday Analog Watch','Hair Clip Set','Slim Card Holder','Canvas Tote Bag','Sunglasses Case','Simple Ring Set','Travel Jewelry Pouch']::text[], 149.00::numeric),
      (17, 'Luggage & Travel', ARRAY['Compact Travel Organizer','Passport Wallet','Packing Cube Set','Foldable Travel Umbrella','Luggage Tag Set','Reusable Travel Bottle Set','Lightweight Daypack','Travel Neck Pillow','Cable Travel Pouch','Collapsible Tote Bag']::text[], 199.00::numeric),
      (18, 'Music', ARRAY['Acoustic Guitar Strings','Digital Metronome','Instrument Cable','Music Practice Notebook','Drumstick Pair','Ukulele Pick Set','Guitar Capo','Microphone Pop Filter','Piano Cleaning Cloth','Music Stand Light']::text[], 99.00::numeric),
      (19, 'Appliances', ARRAY['Electric Kettle','Compact Toaster','Hand Blender','Mini Food Chopper','Portable Fan','Digital Kitchen Timer','Coffee Frother','Garment Steamer','Rechargeable Night Light','Compact Iron']::text[], 499.00::numeric),
      (20, 'Furniture & Decor', ARRAY['Cushion Cover Set','Small Side Table','Woven Storage Basket','Wall Photo Frame','Decorative Throw','Wooden Book Stand','Ceramic Vase','Felt Desk Mat','Compact Shoe Rack','Wall Hanging Planter']::text[], 249.00::numeric)
), generated AS (
    SELECT
      2001 + ((category_order - 1) * 50 + series)::bigint AS id,
      families[((series - 1) % array_length(families, 1)) + 1]
        || ' Series ' || lpad((((series - 1) / array_length(families, 1)) + 1)::text, 2, '0') AS name,
      category || ' essential: ' || families[((series - 1) % array_length(families, 1)) + 1]
        || ' for everyday use.' AS description,
      base_price + (series * 17.25) AS price,
      20 + (series % 31) AS quantity,
      category
    FROM categories CROSS JOIN generate_series(1, 50) AS series
)
INSERT INTO products (id, name, description, price, quantity, category, image_url, active, created_at, updated_at)
SELECT id, name, description, price, quantity, category, NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM generated
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('products', 'id'), (SELECT MAX(id) FROM products));
