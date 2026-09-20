-- Demo data, applied in the dev profile only.
--
-- This lives in db/seed rather than db/migration so tests get a clean schema and
-- build their own fixtures (see application.yml vs application-dev.yml). It also
-- means wiping ./data costs you nothing - restart and the catalog is back.
--
-- V900 keeps it ordered after any real migration you add later.

INSERT INTO categories (public_id, name, slug) VALUES
    ('7c1f5e9a-0001-4a3b-9c2d-000000000001', 'Laptops',     'laptops'),
    ('7c1f5e9a-0002-4a3b-9c2d-000000000002', 'Headphones',  'headphones'),
    ('7c1f5e9a-0003-4a3b-9c2d-000000000003', 'Keyboards',   'keyboards'),
    ('7c1f5e9a-0004-4a3b-9c2d-000000000004', 'Monitors',    'monitors');

INSERT INTO products (public_id, sku, name, description, price, category_id, image_url, active, created_at, version) VALUES
    ('a1b2c3d4-0001-4e5f-8a9b-000000000001', 'LAP-0001',
     'Meridian 14 Ultrabook',
     '14-inch magnesium chassis, 32GB RAM, 1TB NVMe. Weighs under a kilogram.',
     1499.0000, (SELECT id FROM categories WHERE slug = 'laptops'),
     'https://placehold.co/600x400?text=Meridian+14', TRUE, CURRENT_TIMESTAMP, 0),

    ('a1b2c3d4-0002-4e5f-8a9b-000000000002', 'LAP-0002',
     'Meridian 16 Studio',
     '16-inch colour-calibrated display, discrete GPU, built for video work.',
     2399.5000, (SELECT id FROM categories WHERE slug = 'laptops'),
     'https://placehold.co/600x400?text=Meridian+16', TRUE, CURRENT_TIMESTAMP, 0),

    ('a1b2c3d4-0003-4e5f-8a9b-000000000003', 'HPH-0001',
     'Acoustic AN-700 Over-Ear',
     'Hybrid active noise cancellation, 40-hour battery, USB-C.',
     349.0000, (SELECT id FROM categories WHERE slug = 'headphones'),
     'https://placehold.co/600x400?text=AN-700', TRUE, CURRENT_TIMESTAMP, 0),

    ('a1b2c3d4-0004-4e5f-8a9b-000000000004', 'HPH-0002',
     'Acoustic AE-200 In-Ear',
     'Compact in-ear monitors with a balanced tuning and a three-hour charge case.',
     129.9900, (SELECT id FROM categories WHERE slug = 'headphones'),
     'https://placehold.co/600x400?text=AE-200', TRUE, CURRENT_TIMESTAMP, 0),

    ('a1b2c3d4-0005-4e5f-8a9b-000000000005', 'KBD-0001',
     'Tactile 87 Mechanical Keyboard',
     'Tenkeyless, hot-swappable switches, aluminium plate, QMK firmware.',
     179.0000, (SELECT id FROM categories WHERE slug = 'keyboards'),
     'https://placehold.co/600x400?text=Tactile+87', TRUE, CURRENT_TIMESTAMP, 0),

    ('a1b2c3d4-0006-4e5f-8a9b-000000000006', 'KBD-0002',
     'Tactile 60 Compact',
     '60% layout with a rotary encoder. Same switches, less desk.',
     149.0000, (SELECT id FROM categories WHERE slug = 'keyboards'),
     'https://placehold.co/600x400?text=Tactile+60', TRUE, CURRENT_TIMESTAMP, 0),

    ('a1b2c3d4-0007-4e5f-8a9b-000000000007', 'MON-0001',
     'Clearview 27 4K',
     '27-inch 4K IPS, 99% sRGB, single-cable USB-C docking at 90W.',
     649.0000, (SELECT id FROM categories WHERE slug = 'monitors'),
     'https://placehold.co/600x400?text=Clearview+27', TRUE, CURRENT_TIMESTAMP, 0),

    ('a1b2c3d4-0008-4e5f-8a9b-000000000008', 'MON-0002',
     'Clearview 34 Ultrawide',
     '34-inch curved ultrawide, 144Hz, for people who refuse to alt-tab.',
     899.0000, (SELECT id FROM categories WHERE slug = 'monitors'),
     'https://placehold.co/600x400?text=Clearview+34', TRUE, CURRENT_TIMESTAMP, 0),

    -- Inactive on purpose: proves the default listing filters it out.
    ('a1b2c3d4-0009-4e5f-8a9b-000000000009', 'MON-0003',
     'Clearview 24 (discontinued)',
     'Kept in the catalog for order history, hidden from browsing.',
     249.0000, (SELECT id FROM categories WHERE slug = 'monitors'),
     'https://placehold.co/600x400?text=Discontinued', FALSE, CURRENT_TIMESTAMP, 0);
