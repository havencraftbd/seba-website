-- ====================================================================================================
-- HAVENCRAFT SMP — HISTORICAL SALES & EXPENSES ARCHIVE / SEED SCRIPT
-- ====================================================================================================
-- Purpose : Holds the historical 36 sales transactions and 2 expense records from Season 1 (Sep-Oct 2026).
-- Note    : Run this file ONLY if you want to import this specific historical ledger into your database.
--           This has been separated from supabase_schema.sql so seasonal resets stay clean and independent.
-- ====================================================================================================

-- 1. Historical Sales Ledger (36 Records from Google Sheet)
INSERT INTO public.server_sales (sl_no, sale_date, player_name, gamer_tag, purchased_item, spawner_type, quantity, unit_price, total_price, paid_amount, due_amount, payment_method, status, notes)
VALUES
(1, '2026-09-10', 'another_mahfuz', 'JUEL', 'BASIC SPAWNER', 'Cow', 1, 25.00, 25.00, 25.00, 0.00, 'bKash', 'Paid', ''),
(2, '2026-09-10', 'another_mahfuz', 'JUEL', 'ADVANCED SPAWNERS', 'Enderman', 1, 45.00, 45.00, 45.00, 0.00, 'bKash', 'Paid', ''),
(3, '2026-09-10', 'another_mahfuz', 'JUEL', 'ADVANCED SPAWNERS', 'Zombified Piglin', 1, 45.00, 45.00, 45.00, 0.00, 'bKash', 'Paid', ''),
(4, '2026-09-10', 'another_mahfuz', 'JUEL', 'SPECIAL SPAWNERS', 'Iron Golem', 1, 55.00, 55.00, 55.00, 0.00, 'bKash', 'Paid', ''),
(5, '2026-09-10', 'another_mahfuz', 'JUEL', 'SPECIAL SPAWNERS', 'Wither Skeleton', 1, 55.00, 55.00, 55.00, 0.00, 'bKash', 'Paid', ''),
(6, '2026-09-16', 'another_mahfuz1', 'JUEL', 'ADVANCED SPAWNERS', 'Blaze', 1, 45.00, 45.00, 45.00, 0.00, 'bKash', 'Paid', ''),
(7, '2026-09-16', 'another_mahfuz', 'JUEL', 'SPECIAL SPAWNERS', 'Breeze', 2, 55.00, 110.00, 110.00, 0.00, 'bKash', 'Paid', ''),
(8, '2026-09-17', 'another_mahfuz', 'JUEL', 'ELITE RANK', '', 1, 420.00, 420.00, 420.00, 0.00, 'bKash', 'Paid', 'Sheep 1x, Pig 1x, Spider 2x, Guardian 1x, Skeleton 1x, Creeper 1x, Slime 1x'),
(9, '2026-09-17', 'Fardin', 'Fardinax', 'ELITE RANK', '', 1, 420.00, 420.00, 400.00, 20.00, 'bKash', 'Paid', 'Sheep 1x, Cow 1x, Guardian 4x, Creeper 1x, Enderman 1x'),
(10, '2026-09-17', 'Fardina', 'Fardinax', 'SPECIAL SPAWNERS', 'Breeze', 1, 55.00, 55.00, 50.00, 5.00, 'bKash', 'Paid', ''),
(11, '2026-09-17', 'Fardin', 'Fardinax', 'SPECIAL SPAWNERS', 'Ghast', 1, 55.00, 55.00, 50.00, 5.00, 'bKash', 'Paid', 'In Total Discount 30 tk'),
(12, '2026-09-18', 'rh70503', 'RH7Playz67', 'ELITE RANK', '', 1, 420.00, 420.00, 420.00, 0.00, 'bKash', 'Paid', 'Sheep 2x, Creeper x1, Blaze x1, Guardian x4'),
(13, '2026-09-18', 'rh70503', 'RH7Playz67', 'SPECIAL SPAWNERS', 'Breeze', 1, 55.00, 55.00, 55.00, 0.00, 'bKash', 'Paid', ''),
(14, '2026-09-18', 'rh70503', 'RH7Playz67', 'SPECIAL SPAWNERS', 'Wither Skeleton', 1, 55.00, 55.00, 55.00, 0.00, 'bKash', 'Paid', ''),
(15, '2026-09-20', 'kingajmain', 'DarkPhoenix29', 'COMBAT SPAWNER', 'Guardian', 2, 35.00, 70.00, 70.00, 0.00, 'bKash', 'Paid', ''),
(16, '2026-09-20', 'kingajmain', 'DarkPhoenix29', 'ADVANCED SPAWNERS', 'Enderman', 1, 45.00, 45.00, 45.00, 0.00, 'bKash', 'Paid', ''),
(17, '2026-09-20', 'another_mahfuz', 'JUEL', 'SPECIAL SPAWNERS', 'Wither Skeleton', 2, 55.00, 110.00, 110.00, 0.00, 'bKash', 'Paid', ''),
(18, '2026-09-20', 'another_mahfuz', 'JUEL', 'SPECIAL SPAWNERS', 'Breeze', 1, 55.00, 55.00, 55.00, 0.00, 'bKash', 'Paid', ''),
(19, '2026-09-21', 'SaimongamerZ', 'SaimongamerZ', 'ADVANCED SPAWNERS', 'Enderman', 1, 45.00, 45.00, 40.00, 5.00, 'bKash', 'Paid', '5 Tk discount'),
(20, '2026-09-21', 'SaimongamerZ', 'SaimongamerZ', 'SPECIAL SPAWNERS', 'Breeze', 1, 55.00, 55.00, 55.00, 0.00, 'bKash', 'Paid', ''),
(21, '2026-09-21', 'SaimongamerZ', 'SaimongamerZ', 'SPECIAL SPAWNERS', 'Ghast', 1, 55.00, 55.00, 55.00, 0.00, 'bKash', 'Paid', ''),
(22, '2026-09-23', 'NAPA_EXTEND', 'NAPA_EXTEND', 'SPECIAL SPAWNERS', 'Ghast', 1, 55.00, 55.00, 55.00, 0.00, 'bKash', 'Paid', ''),
(23, '2026-09-23', 'NAPA_EXTEND', 'NAPA_EXTEND', 'SPECIAL SPAWNERS', 'Breeze', 1, 55.00, 55.00, 55.00, 0.00, 'bKash', 'Paid', ''),
(24, '2026-09-23', 'NAPA_EXTEND', 'NAPA_EXTEND', 'SPECIAL SPAWNERS', 'Iron Golem', 1, 55.00, 55.00, 0.00, 55.00, 'bKash', 'Paid', 'Free for purchase 2 spawner'),
(25, '2026-09-24', 'fahadamin0300', 'Jax768588', 'ELITE RANK', '', 1, 420.00, 420.00, 420.00, 0.00, 'bKash', 'Paid', 'Cow x1, Sheep x1,Guardian x4, Enderman x1, Creeper x1'),
(26, '2026-09-24', 'munna.5896', 'Munna5896', 'SPECIAL SPAWNERS', 'Breeze', 1, 55.00, 55.00, 50.00, 5.00, 'bKash', 'Paid', '5 tk Discount'),
(27, '2026-09-26', 'GAMERZ_TANJ1D', 'GAMERZ_TANJ1D', 'ELITE RANK', '', 1, 420.00, 420.00, 0.00, 420.00, 'bKash', 'Paid', 'Free Rank For Media'),
(28, '2026-09-28', 'fahadamin0300', 'Jax768588', 'ADVANCED SPAWNERS', 'Magma Cube', 1, 45.00, 45.00, 45.00, 0.00, 'bKash', 'Paid', ''),
(29, '2026-09-28', 'fahadamin0300', 'Jax768588', 'SPECIAL SPAWNERS', 'Wither Skeleton', 1, 55.00, 55.00, 55.00, 0.00, 'bKash', 'Paid', ''),
(30, '2026-09-30', 'DarkPhoenix29', 'DarkPhoenix29', 'SPECIAL SPAWNERS', 'Wither Skeleton', 1, 55.00, 55.00, 55.00, 0.00, 'bKash', 'Paid', ''),
(31, '2026-09-30', 'DarkPhoenix29', 'DarkPhoenix29', 'SPECIAL SPAWNERS', 'Breeze', 1, 55.00, 55.00, 55.00, 0.00, 'bKash', 'Paid', ''),
(32, '2026-10-01', 'another_mahfuz', 'RULER_SHADOW', 'ELITE RANK', '', 1, 420.00, 420.00, 420.00, 0.00, 'bKash', 'Paid', 'Cow 1x, Chicken 1x, Cave Spider 1x, Skeleton 1x, Enderman 1x, Creeper 1x, Piglin 2x'),
(33, '2026-10-03', 'UnknownSoul422', 'UnknownSoul422', 'ELITE RANK', '', 1, 420.00, 420.00, 420.00, 0.00, 'bKash', 'Paid', 'Cow x2, Skeleton x4, Creeper x2, Iron x2 (gift), Blaze x1 (gift)'),
(34, '2026-10-03', 'UnknownSoul422', 'UnknownSoul422', 'SPECIAL SPAWNERS', 'Wither Skeleton', 8, 55.00, 440.00, 400.00, 40.00, 'bKash', 'Paid', '40 TK Discount'),
(35, '2026-10-03', 'UnknownSoul422', 'UnknownSoul422', 'ADVANCED SPAWNERS', 'Blaze', 5, 45.00, 225.00, 205.00, 20.00, 'bKash', 'Paid', '20 TK Discount'),
(36, '2026-10-06', 'another_mahfuz', 'JUEL', 'SPECIAL SPAWNERS', 'Ghast', 1, 55.00, 55.00, 55.00, 0.00, 'bKash', 'Paid', '');

-- 2. Historical Operational Expenses
INSERT INTO public.server_expenses (expense_date, category, title, amount, paid_to, payment_method, invoice_ref, notes)
VALUES
('2026-09-01', 'Server Hosting', 'Primary Dedicated SMP Node (16GB RAM)', 1650.00, 'PebbleHost', 'bKash', 'INV-PB-9821', 'Monthly server host fee'),
('2026-09-05', 'Domain & Web', 'Domain Renewal mc.havencraft.pro', 1200.00, 'Namecheap', 'bKash', 'INV-NC-3310', 'Annual domain DNS renewal');

