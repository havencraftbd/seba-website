-- ====================================================================================================
-- HAVENCRAFT SMP — COMPLETE MASTER DATABASE SCHEMA & SECURITY SPECIFICATION (v3.6)
-- ====================================================================================================
-- Target Database : Supabase (PostgreSQL 15+)
-- Project URL     : https://nvcnowvasqcsygtbwktw.supabase.co
-- Primary Owner   : rajuel55@gmail.com (JU3L_)
-- Architecture    : Zero Data Loss (Idempotent) | 100% Hardened RLS | Granular RBAC | OAuth & Alerts
-- Features        : 12 Tables | Storage Bucket | Anti-Escalation Triggers | Hardened Search Paths | Master Seeds
-- ====================================================================================================
--
-- ╔═════════════════════════════════════════════════════════════════════════════════════════════════════╗
-- ║                                   DATABASE ARCHITECTURE AT A GLANCE                                 ║
-- ╠════╦══════════════════════╦══════════════════════════════╦══════════════╦══════════════╦══════════════╣
-- ║ SL ║ TABLE NAME           ║ PURPOSE / DESCRIPTION        ║ SEED ROWS    ║ PUBLIC RLS   ║ STAFF RLS    ║
-- ╠════╬══════════════════════╬══════════════════════════════╬══════════════╬══════════════╬══════════════╣
-- ║ 01 ║ public.staff_profiles║ Admin & Staff accounts, RBAC ║ Owner (JU3L_)║ None (Blind) ║ Self + users ║
-- ║ 02 ║ public.store_orders  ║ Webstore orders & trx codes  ║ Live Sync    ║ INSERT only  ║ orders perm  ║
-- ║ 03 ║ public.server_sales  ║ Master sales & spawners ledger║ Seasonal Data║ None (Blind) ║ finance perm ║
-- ║ 04 ║ public.server_expenses║ Server bills & hosting costs ║ Seasonal Data║ None (Blind) ║ finance perm ║
-- ║ 05 ║ public.shop_items    ║ In-game catalog & commands   ║ 22 Items     ║ SELECT (All) ║ shop perm    ║
-- ║ 06 ║ public.tickets       ║ Player support tickets       ║ Dynamic      ║ INSERT only  ║ tickets perm ║
-- ║ 07 ║ public.ticket_messages║ Support conversation thread  ║ Dynamic      ║ None (Blind) ║ tickets perm ║
-- ║ 08 ║ public.site_settings ║ Dynamic IP, ports, seasons   ║ Default JSON ║ SELECT (All) ║ settings/shop║
-- ║ 09 ║ public.gallery       ║ Community media showcase     ║ 44 Records   ║ SELECT / SUB ║ gallery perm ║
-- ║ 10 ║ public.audit_logs    ║ Security action audit logs   ║ System Logs  ║ None (Blind) ║ logs perm    ║
-- ║ 11 ║ public.staff_members ║ Public staff directory roster║ 7 Records    ║ SELECT (All) ║ users perm   ║
-- ║ 12 ║ public.app_secrets   ║ Discord webhook & app secrets║ 1 Record     ║ None (Blind) ║ Owner RPC    ║
-- ╚════╩══════════════════════╩══════════════════════════════╩══════════════╩══════════════╩══════════════╝
-- ====================================================================================================


-- ====================================================================================================
-- SECTION 1: EXTENSIONS (প্রয়োজনীয় ডাটাবেজ এক্সটেনশন)
-- ====================================================================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_net";


-- ====================================================================================================
-- SECTION 2: TABLE DEFINITIONS (টেবিল স্কিমা ও কলাম বিবরণ)
-- ====================================================================================================

-- ----------------------------------------------------------------------------------------------------
-- TABLE 1: public.staff_profiles (অ্যাডমিন ও স্টাফ অ্যাকাউন্ট এবং রোল-বেসড পারমিশন)
-- ----------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.staff_profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT UNIQUE NOT NULL,
    full_name TEXT NOT NULL DEFAULT 'Staff Member',
    minecraft_ign TEXT NOT NULL DEFAULT 'Pending',
    discord TEXT DEFAULT 'Not linked',
    role TEXT NOT NULL DEFAULT 'Pending',          -- 'Owner', 'Admin', 'Manager', 'Moderator', 'Helper', 'Pending'
    status TEXT NOT NULL DEFAULT 'pending',        -- 'active', 'pending', 'suspended', 'rejected'
    is_active BOOLEAN NOT NULL DEFAULT false,
    permissions JSONB NOT NULL DEFAULT '{"users":false,"orders":false,"finance":false,"console":false,"settings":false,"tickets":false,"shop":false,"gallery":false,"logs":false}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- কলাম সুরক্ষা (বিদ্যমান টেবিলে কলাম মিসিং থাকলে নিরাপদ অল্টার)
ALTER TABLE public.staff_profiles 
    ADD COLUMN IF NOT EXISTS full_name TEXT DEFAULT 'Staff Member',
    ADD COLUMN IF NOT EXISTS minecraft_ign TEXT DEFAULT 'Pending',
    ADD COLUMN IF NOT EXISTS discord TEXT DEFAULT 'Not linked',
    ADD COLUMN IF NOT EXISTS role TEXT DEFAULT 'Pending',
    ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS permissions JSONB DEFAULT '{"users":false,"orders":false,"finance":false,"console":false,"settings":false,"tickets":false,"shop":false,"gallery":false,"logs":false}'::jsonb,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

COMMENT ON TABLE public.staff_profiles IS 'HavenCraft staff accounts with granular JSONB permissions and roles.';
COMMENT ON COLUMN public.staff_profiles.status IS 'Staff account status: active (approved & full access), pending (awaiting administrator verification), suspended (temporarily disabled), or rejected (application denied; prevented from re-registration loop).';


-- ----------------------------------------------------------------------------------------------------
-- TABLE 2: public.store_orders (ওয়েবসাইট থেকে প্লেয়ারদের দেওয়া শপ অর্ডার)
-- ----------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.store_orders (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_code TEXT UNIQUE NOT NULL,               -- e.g. 'ORD-2026-9812'
    username TEXT NOT NULL,                        -- Minecraft Username
    platform TEXT DEFAULT 'Java',                  -- 'Java' or 'Bedrock'
    item_name TEXT NOT NULL,                       -- e.g. 'Elite Rank', 'Breeze Spawner'
    price TEXT NOT NULL,                           -- e.g. '৳420'
    payment_method TEXT NOT NULL,                  -- 'bKash', 'Nagad', 'Rocket'
    sender_number TEXT NOT NULL,                   -- e.g. '017XXXXXXXX'
    trx_id TEXT NOT NULL,                          -- e.g. 'BK9823101'
    discord_user TEXT,                             -- e.g. 'juel#0001'
    status TEXT NOT NULL DEFAULT 'pending',        -- 'pending', 'delivered', 'rejected'
    delivered_by TEXT,                             -- IGN of staff who delivered
    reject_reason TEXT,                            -- Reason if rejected
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.store_orders
    ADD COLUMN IF NOT EXISTS platform TEXT DEFAULT 'Java',
    ADD COLUMN IF NOT EXISTS discord_user TEXT,
    ADD COLUMN IF NOT EXISTS delivered_by TEXT,
    ADD COLUMN IF NOT EXISTS reject_reason TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

COMMENT ON TABLE public.store_orders IS 'Incoming player webstore orders awaiting staff verification and in-game delivery.';


-- ----------------------------------------------------------------------------------------------------
-- TABLE 3: public.server_sales (অফিসিয়াল সার্ভার সেলস লেজার — স্পনার ও র্যাঙ্ক বিক্রয় রেকর্ড)
-- ----------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.server_sales (
    id BIGSERIAL PRIMARY KEY,
    sl_no INT,                                     -- Serial Number (e.g. 1 to 36+)
    sale_date DATE DEFAULT CURRENT_DATE,           -- Transaction Date
    player_name TEXT NOT NULL,                     -- Customer Name
    gamer_tag TEXT,                                -- Minecraft In-Game Name
    purchased_item TEXT NOT NULL,                  -- e.g. 'BASIC SPAWNER', 'ELITE RANK'
    spawner_type TEXT,                             -- e.g. 'Cow', 'Enderman', 'Breeze'
    quantity INT DEFAULT 1,                        -- Quantity
    unit_price NUMERIC(10, 2) DEFAULT 0.00,        -- Price per unit (BDT)
    total_price NUMERIC(10, 2) DEFAULT 0.00,       -- Total Bill (BDT)
    paid_amount NUMERIC(10, 2) DEFAULT 0.00,       -- Amount Paid (BDT)
    due_amount NUMERIC(10, 2) DEFAULT 0.00,        -- Outstanding Due (BDT)
    payment_method TEXT DEFAULT 'bKash',           -- 'bKash', 'Nagad', etc.
    status TEXT DEFAULT 'Paid',                    -- 'Paid', 'Due'
    notes TEXT,                                    -- Item breakdowns / Discount info
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

COMMENT ON TABLE public.server_sales IS 'Direct manual and spreadsheet sales ledger including spawners and ranks.';


-- ----------------------------------------------------------------------------------------------------
-- TABLE 4: public.server_expenses (সার্ভার পরিচালনা ব্যয় — হোস্টিং, ডোমেন ও প্লাগিন বিল)
-- ----------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.server_expenses (
    id BIGSERIAL PRIMARY KEY,
    expense_date DATE DEFAULT CURRENT_DATE,        -- Expense Billing Date
    category TEXT NOT NULL,                        -- 'Server Hosting', 'Domain & Web', 'Plugins & Dev', 'Marketing & Ads', 'Events & Giveaways', 'Maintenance'
    title TEXT NOT NULL,                           -- Expense description (e.g. 'Apex 16GB Dedicated Node')
    amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,   -- Bill Amount (BDT)
    paid_to TEXT,                                  -- Vendor / Provider (e.g. 'PebbleHost', 'Namecheap')
    payment_method TEXT DEFAULT 'bKash',           -- 'bKash', 'Bank Transfer', 'Card'
    invoice_ref TEXT,                              -- Invoice or Transaction ID
    notes TEXT,                                    -- Billing breakdown or remarks
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

COMMENT ON TABLE public.server_expenses IS 'Operational costs and infrastructure bills for accurate treasury accounting.';


-- ----------------------------------------------------------------------------------------------------
-- TABLE 5: public.shop_items (অফিসিয়াল শপ ক্যাটালগ ও কনসোল কমান্ডস)
-- ----------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.shop_items (
    id TEXT PRIMARY KEY,                           -- Unique item ID e.g. 'rank-elite', 'spawner-breeze'
    type TEXT NOT NULL,                            -- 'rank' or 'spawner'
    name TEXT NOT NULL,                            -- Display Name
    category TEXT NOT NULL,                        -- 'Basic', 'Combat', 'Advanced', 'Special', 'Starter Tier', etc.
    bdt NUMERIC(10, 2) NOT NULL DEFAULT 0.00,      -- Price in BDT
    usd NUMERIC(10, 2) NOT NULL DEFAULT 0.00,      -- Price in USD
    command TEXT NOT NULL,                         -- Console Command with {username} placeholder
    perks TEXT,                                    -- Feature summary or farm utility
    active BOOLEAN DEFAULT true,                   -- Visibility on webstore
    icon TEXT DEFAULT 'bi-box-seam',               -- Bootstrap icon class
    icon_color TEXT DEFAULT 'var(--primary)',      -- UI Accent Color
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

COMMENT ON TABLE public.shop_items IS 'Official shop catalog items, tiers, pricing, and automated console commands.';


-- ----------------------------------------------------------------------------------------------------
-- TABLE 6: public.tickets (প্লেয়ারদের সাপোর্ট টিকিট)
-- ----------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.tickets (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    ticket_code TEXT UNIQUE,                       -- e.g. 'TCK-4819'
    user_name TEXT NOT NULL,                       -- Player IGN or Name
    category TEXT DEFAULT 'General',               -- 'General', 'Billing', 'Bug Report', 'Ban Appeal'
    subject TEXT NOT NULL,                         -- Issue Title
    description TEXT,                              -- Detailed explanation
    status TEXT NOT NULL DEFAULT 'open',           -- 'open', 'resolved', 'closed'
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.tickets
    ADD COLUMN IF NOT EXISTS category TEXT DEFAULT 'General',
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'open',
    ADD COLUMN IF NOT EXISTS ai_enabled BOOLEAN DEFAULT true,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

COMMENT ON TABLE public.tickets IS 'Customer support tickets created by players.';


-- ----------------------------------------------------------------------------------------------------
-- TABLE 7: public.ticket_messages (টিকেটের অভ্যন্তরীণ চ্যাট কনভারসেশন)
-- ----------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.ticket_messages (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    ticket_id BIGINT REFERENCES public.tickets(id) ON DELETE CASCADE,
    sender TEXT NOT NULL,                          -- 'player' or 'staff'
    sender_name TEXT NOT NULL,                     -- Staff IGN or Player IGN
    message TEXT NOT NULL,                         -- Chat message body
    created_at TIMESTAMPTZ DEFAULT NOW()
);

COMMENT ON TABLE public.ticket_messages IS 'Threaded conversation history for support tickets.';


-- ----------------------------------------------------------------------------------------------------
-- TABLE 8: public.site_settings (ডাইনামিক কনফিগারেশন — আইপি, পোর্ট, সিজন ও ক্যাশব্যাক)
-- ----------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.site_settings (
    key TEXT PRIMARY KEY,                          -- e.g. 'server_info', 'shop_catalog'
    value JSONB NOT NULL,                          -- Config payload
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

COMMENT ON TABLE public.site_settings IS 'Central site configuration and feature flags stored as JSONB.';


-- ----------------------------------------------------------------------------------------------------
-- TABLE 9: public.gallery (কমিউনিটি স্ক্রিনশট ও মিডিয়া গ্যালারি)
-- ----------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.gallery (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    image_url TEXT NOT NULL,                       -- Public CDN or media URL
    caption TEXT NOT NULL,                         -- Media title or description
    category TEXT DEFAULT 'SMP Community',         -- 'SMP Community', 'Builds', 'PvP War'
    uploaded_by TEXT DEFAULT 'Staff',              -- Staff or Player credit
    featured BOOLEAN DEFAULT false,                -- Pin to top of homepage
    status TEXT NOT NULL DEFAULT 'approved',       -- 'approved', 'submissions', 'archived'
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.gallery
    ADD COLUMN IF NOT EXISTS featured BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'approved',
    ADD COLUMN IF NOT EXISTS category TEXT DEFAULT 'SMP Community',
    ADD COLUMN IF NOT EXISTS uploaded_by TEXT DEFAULT 'Staff';

COMMENT ON TABLE public.gallery IS 'Community builds, events, and player screenshot showcase.';


-- ----------------------------------------------------------------------------------------------------
-- TABLE 10: public.audit_logs (স্টাফ অ্যাকশন ও সিকিউরিটি অডিট ট্রেইল)
-- ----------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    staff_name TEXT NOT NULL,                      -- Staff Minecraft IGN
    staff_role TEXT,                               -- Staff Role during action
    category TEXT NOT NULL,                        -- 'Finance Ledger', 'Store Orders', 'Staff Roles'
    action TEXT NOT NULL,                          -- e.g. 'CREATE_SALE', 'DELIVER_ORDER', 'CHANGE_ROLE'
    target TEXT NOT NULL,                          -- Target player or order code
    result TEXT DEFAULT 'Success',                 -- 'Success', 'Failed'
    details JSONB DEFAULT '{}'::jsonb,             -- Full before/after metadata
    created_at TIMESTAMPTZ DEFAULT NOW()
);

COMMENT ON TABLE public.audit_logs IS 'Tamper-proof audit log records of all staff actions.';


-- ----------------------------------------------------------------------------------------------------
-- TABLE 11: public.staff_members (পাবলিক ওয়েবসাইট স্টাফ শোকেস — ইন-গেম ও পাবলিক টিম)
-- ----------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.staff_members (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,                             -- Minecraft In-Game Name (e.g. 'JU3L_', '.Rafi_PRX', 'IM_SAM_JI')
    role TEXT NOT NULL DEFAULT 'Officer',          -- 'Owner', 'Admin', 'Manager', 'Moderator', 'Officer', 'Developer'
    category TEXT NOT NULL DEFAULT 'Support',       -- 'Management', 'Moderation', 'Support'
    role_class TEXT DEFAULT 'role-officer',         -- 'role-owner', 'role-admin', 'role-mod', 'role-officer'
    discord TEXT DEFAULT '',                        -- Discord tag/username
    description TEXT DEFAULT '',                    -- Bio / Description
    skin TEXT DEFAULT '',                           -- Custom texture URL, MineSkin link, or blank for automatic Mojang skin
    order_index INT DEFAULT 0,                      -- Sorting order on website
    is_active BOOLEAN NOT NULL DEFAULT true,        -- Toggle visibility on website
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.staff_members
    ADD COLUMN IF NOT EXISTS role TEXT DEFAULT 'Officer',
    ADD COLUMN IF NOT EXISTS category TEXT DEFAULT 'Support',
    ADD COLUMN IF NOT EXISTS role_class TEXT DEFAULT 'role-officer',
    ADD COLUMN IF NOT EXISTS discord TEXT DEFAULT '',
    ADD COLUMN IF NOT EXISTS description TEXT DEFAULT '',
    ADD COLUMN IF NOT EXISTS skin TEXT DEFAULT '',
    ADD COLUMN IF NOT EXISTS order_index INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true;

CREATE UNIQUE INDEX IF NOT EXISTS idx_staff_members_name ON public.staff_members (lower(name));
COMMENT ON TABLE public.staff_members IS 'Public Minecraft server staff roster displayed on /staff with skin URLs.';


-- ----------------------------------------------------------------------------------------------------
-- TABLE 12: public.app_secrets (গোপন ক্রেডেনশিয়াল ও ডিসকর্ড অ্যালার্ট কনফিগ — জিরো পাবলিক এক্সেস)
-- ----------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.app_secrets (
    key TEXT PRIMARY KEY,
    value TEXT,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.app_secrets ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.app_secrets FROM anon, authenticated;
COMMENT ON TABLE public.app_secrets IS 'Secure store for application secrets and Discord webhook URLs (RLS locked, zero public policies).';


-- ====================================================================================================
-- SECTION 3: PERFORMANCE INDEXES (গতিশীল সার্চ ও ইনডেক্সিং)
-- ====================================================================================================
CREATE INDEX IF NOT EXISTS idx_staff_email ON public.staff_profiles(lower(email));
CREATE INDEX IF NOT EXISTS idx_store_orders_status ON public.store_orders(status);
CREATE INDEX IF NOT EXISTS idx_store_orders_code ON public.store_orders(order_code);
CREATE INDEX IF NOT EXISTS idx_server_sales_date ON public.server_sales(sale_date DESC);
CREATE INDEX IF NOT EXISTS idx_server_sales_player ON public.server_sales(player_name);
CREATE INDEX IF NOT EXISTS idx_server_sales_status ON public.server_sales(status);
CREATE INDEX IF NOT EXISTS idx_server_expenses_date ON public.server_expenses(expense_date DESC);
CREATE INDEX IF NOT EXISTS idx_server_expenses_cat ON public.server_expenses(category);
CREATE INDEX IF NOT EXISTS idx_shop_items_type ON public.shop_items(type);
CREATE INDEX IF NOT EXISTS idx_shop_items_active ON public.shop_items(active);
CREATE INDEX IF NOT EXISTS idx_tickets_code ON public.tickets(ticket_code);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON public.tickets(status);
CREATE INDEX IF NOT EXISTS idx_ticket_messages_ticket ON public.ticket_messages(ticket_id);
CREATE INDEX IF NOT EXISTS idx_gallery_status ON public.gallery(status);
CREATE INDEX IF NOT EXISTS idx_audit_created ON public.audit_logs(created_at DESC);


-- ====================================================================================================
-- SECTION 4: SECURITY FUNCTIONS & RBAC TRIGGERS (কঠোর নিরাপত্তা ফাংশন)
-- ====================================================================================================

-- ১. ওনার ভেরিফিকেশন ফাংশন (Zero Bypass - শুধুমাত্র rajuel55@gmail.com)
CREATE OR REPLACE FUNCTION public.is_haven_owner()
RETURNS BOOLEAN AS $$
BEGIN
    RETURN lower(COALESCE(auth.jwt() ->> 'email', '')) = 'rajuel55@gmail.com'
        OR EXISTS (
            SELECT 1 FROM public.staff_profiles
            WHERE (id = auth.uid() OR lower(email) = lower(COALESCE(auth.jwt() ->> 'email', '')))
              AND is_active = true
              AND lower(COALESCE(status, 'active')) NOT IN ('pending', 'rejected', 'suspended')
              AND lower(COALESCE(role, '')) = 'owner'
        );
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public;

-- ২. স্টাফ গ্র্যানুলার পারমিশন চেকার ফাংশন
CREATE OR REPLACE FUNCTION public.has_haven_perm(perm_key TEXT)
RETURNS BOOLEAN AS $$
DECLARE
    v_active BOOLEAN;
    v_status TEXT;
    v_role TEXT;
    v_perms JSONB;
BEGIN
    -- ওনার সবসময় সব পারমিশন বাইপাস পাবে
    IF public.is_haven_owner() THEN
        RETURN true;
    END IF;

    -- লগইন ছাড়া কোনো পারমিশন নেই
    IF auth.uid() IS NULL THEN
        RETURN false;
    END IF;

    SELECT is_active, status, role, permissions
    INTO v_active, v_status, v_role, v_perms
    FROM public.staff_profiles
    WHERE id = auth.uid();

    -- অ্যাকাউন্ট অ্যাক্টিভ ও অ্যাপ্রুভড না থাকলে বাতিল
    IF v_active IS NOT TRUE OR v_status <> 'active' OR v_role = 'Pending' THEN
        RETURN false;
    END IF;

    IF perm_key = 'any' THEN
        RETURN true;
    END IF;

    -- এলায়েন্স/মিউচুয়াল হ্যান্ডলিং: finance ও orders একে অপরের পরিপূরক পারমিশন
    IF perm_key = 'finance' THEN
        RETURN COALESCE((v_perms ->> 'finance')::boolean, (v_perms ->> 'orders')::boolean, false);
    END IF;
    IF perm_key = 'orders' THEN
        RETURN COALESCE((v_perms ->> 'orders')::boolean, (v_perms ->> 'finance')::boolean, false);
    END IF;

    RETURN COALESCE((v_perms ->> perm_key)::boolean, false);
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public;

-- ৩. রোল র‍্যাংক ও সেলফ-প্রমোশন প্রতিরোধক ট্রিগার (ব্রাউজার বা ডাটাবেজ থেকে এস্কেলেশন ব্লক)
CREATE OR REPLACE FUNCTION public.get_role_rank(r text)
RETURNS int AS $$
BEGIN
    RETURN CASE lower(trim(coalesce(r, '')))
        WHEN 'owner' THEN 100
        WHEN 'sr-admin' THEN 90
        WHEN 'sradmin' THEN 90
        WHEN 'co-owner' THEN 90
        WHEN 'manager' THEN 80
        WHEN 'developer' THEN 75
        WHEN 'admin' THEN 70
        WHEN 'administrator' THEN 70
        WHEN 'moderator' THEN 60
        WHEN 'mod' THEN 60
        WHEN 'officer' THEN 50
        WHEN 'helper' THEN 40
        WHEN 'builder' THEN 30
        WHEN 'staff' THEN 20
        ELSE 10
    END;
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public;

CREATE OR REPLACE FUNCTION public.guard_staff_profile_changes()
RETURNS TRIGGER AS $$
DECLARE
    caller_role text;
    caller_rank int;
    target_old_rank int;
    target_new_rank int;
BEGIN
    IF current_setting('request.jwt.claim.role', true) = 'authenticated' THEN
        -- প্রাইমারি কি (id) ক্লায়েন্ট থেকে ওভাররাইট করা সম্পূর্ণ নিষিদ্ধ (Anti-PK Hijack)
        IF TG_OP = 'UPDATE' AND NEW.id IS DISTINCT FROM OLD.id THEN
            RAISE EXCEPTION 'Security Violation: Primary key (id) of staff_profiles cannot be modified.';
        END IF;

        -- ওনার বা 'users' এডিট পারমিশন (hvn_can_edit) ছাড়া অন্য কেউ রোল/পারমিশন পরিবর্তন বা অন্যের প্রোফাইল এডিট করতে পারবে না
        IF NOT public.is_haven_owner() AND NOT public.hvn_can_edit('users') THEN
            IF TG_OP = 'INSERT' THEN
                NEW.role := 'Pending';
                NEW.status := 'pending';
                NEW.is_active := false;
                NEW.permissions := '{"users":false,"orders":false,"finance":false,"console":false,"settings":false,"tickets":false,"shop":false,"gallery":false,"logs":false}'::jsonb;
            ELSIF TG_OP = 'UPDATE' THEN
                IF OLD.id IS DISTINCT FROM auth.uid() THEN
                    RAISE EXCEPTION 'Access Denied: You cannot modify another staff profile in View-Only mode.';
                END IF;
                NEW.role := OLD.role;
                NEW.status := OLD.status;
                NEW.is_active := OLD.is_active;
                NEW.permissions := OLD.permissions;
                NEW.email := OLD.email;
            END IF;
        END IF;

        -- ওনার ছাড়া অন্য যেকোনো স্টাফের ক্ষেত্রে কঠোর রোল হায়ারার্কি ও অ্যান্টি-এসকেলেশন বলবৎ
        IF NOT public.is_haven_owner() THEN
            -- নিজের প্রোফাইল নিজে আপডেট করার সময় কখনোই নিজের রোল/স্ট্যাটাস/পারমিশন/ইমেইল পরিবর্তন করা যাবে না
            IF TG_OP = 'UPDATE' AND OLD.id = auth.uid() THEN
                NEW.role := OLD.role;
                NEW.status := OLD.status;
                NEW.is_active := OLD.is_active;
                NEW.permissions := OLD.permissions;
                NEW.email := OLD.email;
            ELSE
                -- অন্য স্টাফের প্রোফাইল এডিট বা ইনসার্ট করার সময় রোল র‍্যাংক যাচাই
                SELECT role INTO caller_role FROM public.staff_profiles WHERE id = auth.uid();
                caller_rank := public.get_role_rank(caller_role);
                target_old_rank := public.get_role_rank(OLD.role);
                target_new_rank := public.get_role_rank(NEW.role);

                -- ১. সমান বা উপরের কোনো রোল পরিবর্তন বা এক্সেস টাচ করা সম্পূর্ণ নিষিদ্ধ
                IF TG_OP = 'UPDATE' AND target_old_rank >= caller_rank THEN
                    RAISE EXCEPTION 'Access Denied: You cannot modify a staff account equal to or higher than your role.';
                END IF;

                -- ২. নিজের রোলের সমান বা উপরের কোনো রোলে কাউকে প্রমোট করা সম্পূর্ণ নিষিদ্ধ
                IF target_new_rank >= caller_rank THEN
                    RAISE EXCEPTION 'Access Denied: You cannot assign a role equal to or higher than your own role.';
                END IF;

                -- ৩. নন-ওনার কোনোভাবেই কারো কাস্টম পারমিশন নিতে বা দিতে পারবে না (পারমিশন স্বয়ংক্রিয়ভাবে রোল ডিফল্টে লক থাকবে)
                IF NEW.permissions IS DISTINCT FROM OLD.permissions THEN
                    NEW.permissions := jsonb_build_object(
                        'users', false,
                        'orders', target_new_rank >= 60 AND NEW.role <> 'Developer' AND NEW.role <> 'Builder',
                        'finance', target_new_rank >= 60 AND NEW.role <> 'Developer' AND NEW.role <> 'Builder',
                        'console', target_new_rank >= 70 OR NEW.role = 'Developer',
                        'settings', NEW.role = 'Developer',
                        'tickets', target_new_rank >= 40 AND NEW.role <> 'Builder',
                        'shop', target_new_rank >= 70,
                        'gallery', target_new_rank >= 50 OR NEW.role = 'Builder',
                        'logs', target_new_rank >= 70 OR NEW.role = 'Developer'
                    );
                END IF;
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = public;

DROP TRIGGER IF EXISTS trg_guard_staff ON public.staff_profiles;
CREATE TRIGGER trg_guard_staff
BEFORE INSERT OR UPDATE ON public.staff_profiles
FOR EACH ROW EXECUTE FUNCTION public.guard_staff_profile_changes();

-- ৪. স্বয়ংক্রিয় সাইন-আপ হ্যান্ডলার (Google/Discord OAuth ও Auth রেজিস্ট্রেশনে staff_profiles এ এন্ট্রি হবে)
-- শুধুমাত্র প্রাইমারি ওনার (rajuel55@gmail.com) অটোমেটিক Owner রোল পাবে, বাকিরা সবাই Pending Verification থাকবে।
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_is_owner BOOLEAN;
    v_dname TEXT;
    v_discord TEXT;
BEGIN
    v_is_owner := (lower(coalesce(NEW.email, '')) = 'rajuel55@gmail.com');

    v_dname := coalesce(
        nullif(NEW.raw_user_meta_data->>'full_name', ''),
        nullif(NEW.raw_user_meta_data->>'name', ''),
        nullif(NEW.raw_user_meta_data->'custom_claims'->>'global_name', ''),
        nullif(NEW.raw_user_meta_data->>'global_name', ''),
        nullif(NEW.raw_user_meta_data->>'preferred_username', ''),
        split_part(coalesce(NEW.email, 'staff'), '@', 1)
    );

    -- Extract Discord username for Discord OAuth only; Google/Email defaults to 'Not linked'
    v_discord := coalesce(
        nullif(NEW.raw_user_meta_data->'custom_claims'->>'preferred_username', ''),
        nullif(NEW.raw_user_meta_data->>'preferred_username', ''),
        nullif(NEW.raw_user_meta_data->>'user_name', ''),
        'Not linked'
    );

    IF v_is_owner THEN
        INSERT INTO public.staff_profiles (id, email, full_name, minecraft_ign, discord, role, is_active, status, permissions)
        VALUES (
            NEW.id,
            NEW.email,
            'Juel',
            'JU3L_',
            coalesce(nullif(v_discord, 'Not linked'), 'Not linked'),
            'Owner',
            true,
            'active',
            '{"users":true,"orders":true,"finance":true,"console":true,"settings":true,"tickets":true,"shop":true,"gallery":true,"logs":true}'::jsonb
        )
        ON CONFLICT (id) DO UPDATE SET
            role = 'Owner',
            is_active = true,
            status = 'active';
    ELSE
        INSERT INTO public.staff_profiles (id, email, full_name, minecraft_ign, discord, role, is_active, status, permissions)
        VALUES (
            NEW.id,
            NEW.email,
            v_dname,
            'Pending',
            v_discord,
            'Pending Verification',
            false,
            'pending',
            '{"users":false,"orders":false,"finance":false,"console":false,"settings":false,"tickets":false,"shop":false,"gallery":false,"logs":false}'::jsonb
        )
        ON CONFLICT (id) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();


-- ----------------------------------------------------------------------------------------------------
-- ৫. গ্র্যানুলার ভিউ বনাম এডিট পারমিশন হেল্পার ফাংশনসমূহ (Granular View vs Edit Helpers)
-- ----------------------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.hvn_is_owner()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    lower(coalesce(auth.jwt() ->> 'email', '')) = 'rajuel55@gmail.com'
    OR EXISTS (
      SELECT 1
      FROM public.staff_profiles sp
      WHERE (sp.id = auth.uid() OR lower(sp.email) = lower(coalesce(auth.jwt() ->> 'email', '')))
        AND sp.is_active = true
        AND lower(coalesce(sp.status, 'active')) NOT IN ('pending', 'rejected', 'suspended')
        AND lower(coalesce(sp.role, '')) = 'owner'
    );
$$;

CREATE OR REPLACE FUNCTION public.hvn_is_active_staff()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    public.hvn_is_owner()
    OR EXISTS (
      SELECT 1
      FROM public.staff_profiles sp
      WHERE (sp.id = auth.uid() OR lower(sp.email) = lower(coalesce(auth.jwt() ->> 'email', '')))
        AND sp.is_active = true
        AND lower(coalesce(sp.status, 'active')) NOT IN ('pending', 'rejected', 'suspended')
        AND lower(coalesce(sp.role, '')) IN ('owner', 'sr-admin', 'sradmin', 'co-owner', 'admin', 'administrator', 'manager', 'moderator', 'mod', 'officer', 'helper', 'developer', 'builder', 'staff')
    );
$$;

CREATE OR REPLACE FUNCTION public.hvn_can_view(mod_key text)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_perms jsonb;
  v_role text;
BEGIN
  IF public.hvn_is_owner() THEN
    RETURN true;
  END IF;

  SELECT sp.permissions, lower(coalesce(sp.role, ''))
    INTO v_perms, v_role
  FROM public.staff_profiles sp
  WHERE (sp.id = auth.uid() OR lower(sp.email) = lower(coalesce(auth.jwt() ->> 'email', '')))
    AND sp.is_active = true
    AND lower(coalesce(sp.status, 'active')) NOT IN ('pending', 'rejected', 'suspended')
  LIMIT 1;

  IF v_role IS NULL OR v_role = 'pending' THEN
    RETURN false;
  END IF;

  IF mod_key = 'dashboard' THEN
    RETURN true;
  END IF;

  IF v_perms IS NULL THEN
    RETURN false;
  END IF;

  -- Support mutual orders <-> finance view alias
  IF mod_key IN ('orders', 'finance') THEN
    RETURN coalesce((v_perms ->> 'orders')::boolean, false)
        OR coalesce((v_perms ->> 'finance')::boolean, false);
  END IF;

  RETURN coalesce((v_perms ->> mod_key)::boolean, false);
END;
$$;

CREATE OR REPLACE FUNCTION public.hvn_can_edit(mod_key text)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_perms jsonb;
  v_role text;
  v_edit_key text := mod_key || '_edit';
BEGIN
  IF public.hvn_is_owner() THEN
    RETURN true;
  END IF;

  IF NOT public.hvn_can_view(mod_key) THEN
    RETURN false;
  END IF;

  SELECT sp.permissions, lower(coalesce(sp.role, ''))
    INTO v_perms, v_role
  FROM public.staff_profiles sp
  WHERE (sp.id = auth.uid() OR lower(sp.email) = lower(coalesce(auth.jwt() ->> 'email', '')))
    AND sp.is_active = true
    AND lower(coalesce(sp.status, 'active')) NOT IN ('pending', 'rejected', 'suspended')
  LIMIT 1;

  IF v_perms IS NULL THEN
    RETURN false;
  END IF;

  -- Global read_only flag blocks all writes
  IF coalesce((v_perms ->> 'read_only')::boolean, false) = true THEN
    RETURN false;
  END IF;

  -- Audit logs are never editable by non-owners
  IF mod_key = 'logs' THEN
    RETURN false;
  END IF;

  -- Mutual orders <-> finance edit check
  IF mod_key IN ('orders', 'finance') THEN
    IF (v_perms ? 'orders_edit') OR (v_perms ? 'finance_edit') THEN
      RETURN coalesce((v_perms ->> 'orders_edit')::boolean, false)
          OR coalesce((v_perms ->> 'finance_edit')::boolean, false);
    END IF;
    RETURN coalesce((v_perms ->> 'orders')::boolean, false)
        OR coalesce((v_perms ->> 'finance')::boolean, false);
  END IF;

  -- Explicit <mod>_edit flag if present; otherwise fallback to legacy boolean
  IF (v_perms ? v_edit_key) THEN
    RETURN coalesce((v_perms ->> v_edit_key)::boolean, false);
  END IF;

  RETURN coalesce((v_perms ->> mod_key)::boolean, false);
END;
$$;


-- ----------------------------------------------------------------------------------------------------
-- ৬. ডিসকর্ড ওয়েবহুক অ্যালার্ট ফাংশনসমূহ (Discord Webhook Notifications via pg_net)
-- ----------------------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.haven_get_discord_config()
RETURNS TABLE (webhook_url TEXT, alert_orders BOOLEAN, alert_tickets BOOLEAN, alert_staff BOOLEAN)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, net, extensions, pg_temp AS $$
DECLARE
    v_url TEXT;
BEGIN
    -- Prevent direct PostgREST RPC invocation by non-owners; allow internal DB triggers
    IF pg_trigger_depth() = 0 AND NOT public.is_haven_owner() THEN
        RAISE EXCEPTION 'Forbidden: Direct access to Discord configuration is denied.';
    END IF;

    SELECT value INTO v_url FROM public.app_secrets WHERE key = 'discord_webhook_url';
    webhook_url := COALESCE(v_url, '');
    alert_orders := COALESCE((SELECT value FROM public.app_secrets WHERE key = 'discord_alert_orders'), 'false') = 'true';
    alert_tickets := COALESCE((SELECT value FROM public.app_secrets WHERE key = 'discord_alert_tickets'), 'false') = 'true';
    alert_staff := COALESCE((SELECT value FROM public.app_secrets WHERE key = 'discord_alert_staff'), 'false') = 'true';
    RETURN NEXT;
END;
$$;

REVOKE ALL ON FUNCTION public.haven_get_discord_config() FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.haven_send_discord_embed(p_payload JSONB)
RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, net, extensions, pg_temp AS $$
DECLARE
    v_url TEXT;
BEGIN
    -- Prevent direct PostgREST RPC invocation by non-owners; allow internal DB triggers
    IF pg_trigger_depth() = 0 AND NOT public.is_haven_owner() THEN
        RAISE EXCEPTION 'Forbidden: Direct access to Discord webhook sender is denied.';
    END IF;

    SELECT webhook_url INTO v_url FROM public.haven_get_discord_config();
    IF v_url IS NULL OR v_url NOT LIKE 'https://%' THEN
        RETURN FALSE;
    END IF;

    PERFORM net.http_post(
        url := v_url,
        body := p_payload,
        headers := jsonb_build_object('Content-Type', 'application/json'),
        timeout_milliseconds := 5000
    );
    RETURN TRUE;
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE 'Forbidden:%' THEN
        RAISE;
    END IF;
    RETURN FALSE;
END;
$$;

REVOKE ALL ON FUNCTION public.haven_send_discord_embed(JSONB) FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.haven_on_order_insert()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, net, extensions, pg_temp AS $$
DECLARE
    v_cfg RECORD;
    v_payload JSONB;
BEGIN
    SELECT * INTO v_cfg FROM public.haven_get_discord_config();
    IF NOT v_cfg.alert_orders THEN RETURN NEW; END IF;

    v_payload := jsonb_build_object(
        'username', 'HavenCraft Alerts',
        'embeds', jsonb_build_array(jsonb_build_object(
            'title', 'New Store Order — ' || COALESCE(NEW.order_code, '#' || NEW.id::TEXT),
            'color', 51350,
            'fields', jsonb_build_array(
                jsonb_build_object('name', 'Player',  'value', COALESCE(NEW.username, '-'), 'inline', true),
                jsonb_build_object('name', 'Platform','value', COALESCE(NEW.platform, 'Java'), 'inline', true),
                jsonb_build_object('name', 'Item',    'value', COALESCE(NEW.item_name, '-'), 'inline', true),
                jsonb_build_object('name', 'Amount',  'value', COALESCE(NEW.price, '-'), 'inline', true),
                jsonb_build_object('name', 'Payment', 'value', COALESCE(NEW.payment_method, '-'), 'inline', true),
                jsonb_build_object('name', 'TrxID',   'value', COALESCE(NEW.trx_id, '-'), 'inline', true)
            ),
            'footer', jsonb_build_object('text', 'HavenCraft SMP • Admin Dashboard'),
            'timestamp', to_char(NEW.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
        ))
    );

    PERFORM public.haven_send_discord_embed(v_payload);
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.haven_on_order_insert() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS trg_discord_order_alert ON public.store_orders;
CREATE TRIGGER trg_discord_order_alert
    AFTER INSERT ON public.store_orders
    FOR EACH ROW EXECUTE FUNCTION public.haven_on_order_insert();

CREATE OR REPLACE FUNCTION public.haven_on_ticket_insert()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, net, extensions, pg_temp AS $$
DECLARE
    v_cfg RECORD;
    v_payload JSONB;
BEGIN
    SELECT * INTO v_cfg FROM public.haven_get_discord_config();
    IF NOT v_cfg.alert_tickets THEN RETURN NEW; END IF;

    v_payload := jsonb_build_object(
        'username', 'HavenCraft Alerts',
        'embeds', jsonb_build_array(jsonb_build_object(
            'title', 'New Support Ticket — ' || COALESCE(NEW.ticket_code, '#' || NEW.id::TEXT),
            'color', 3722744,
            'fields', jsonb_build_array(
                jsonb_build_object('name', 'Player',    'value', COALESCE(NEW.user_name, '-'), 'inline', true),
                jsonb_build_object('name', 'Category',  'value', COALESCE(NEW.category, 'General'), 'inline', true),
                jsonb_build_object('name', 'Subject',   'value', COALESCE(NEW.subject, '-'), 'inline', false)
            ),
            'footer', jsonb_build_object('text', 'HavenCraft SMP • Admin Dashboard'),
            'timestamp', to_char(NEW.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
        ))
    );

    PERFORM public.haven_send_discord_embed(v_payload);
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.haven_on_ticket_insert() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS trg_discord_ticket_alert ON public.tickets;
CREATE TRIGGER trg_discord_ticket_alert
    AFTER INSERT ON public.tickets
    FOR EACH ROW EXECUTE FUNCTION public.haven_on_ticket_insert();

-- ----------------------------------------------------------------------------------------------------
-- 4.5. HavenCraft AI Support Auto-Responder Triggers (via pg_net)
-- ----------------------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.haven_trigger_ticket_ai()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, net, extensions, pg_temp AS $$
DECLARE
    v_secret TEXT;
    v_url TEXT;
BEGIN
    SELECT value INTO v_secret FROM public.app_secrets WHERE key = 'ticket_ai_secret';
    SELECT value INTO v_url FROM public.app_secrets WHERE key = 'ticket_ai_webhook_url';

    IF v_url IS NULL OR v_url = '' THEN
        v_url := 'https://admin.havencraft.pro/api/ticket-ai';
    END IF;

    -- Asynchronously trigger Vercel Serverless Function via pg_net
    PERFORM net.http_post(
        url := v_url,
        body := jsonb_build_object(
            'action', 'auto_reply',
            'ticket_id', NEW.id
        ),
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'x-ticket-ai-secret', COALESCE(v_secret, '')
        ),
        timeout_milliseconds := 8000
    );

    RETURN NEW;
EXCEPTION WHEN OTHERS THEN
    -- Fail-safe: Never block player ticket creation if AI gateway is temporarily down
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.haven_trigger_ticket_ai() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS trg_ticket_ai_auto_reply ON public.tickets;
CREATE TRIGGER trg_ticket_ai_auto_reply
    AFTER INSERT ON public.tickets
    FOR EACH ROW EXECUTE FUNCTION public.haven_trigger_ticket_ai();

CREATE OR REPLACE FUNCTION public.haven_trigger_ticket_message_ai()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, net, extensions, pg_temp AS $$
DECLARE
    v_secret TEXT;
    v_url TEXT;
BEGIN
    -- Only trigger for incoming player messages
    IF NEW.sender <> 'player' THEN
        RETURN NEW;
    END IF;

    SELECT value INTO v_secret FROM public.app_secrets WHERE key = 'ticket_ai_secret';
    SELECT value INTO v_url FROM public.app_secrets WHERE key = 'ticket_ai_webhook_url';

    IF v_url IS NULL OR v_url = '' THEN
        v_url := 'https://admin.havencraft.pro/api/ticket-ai';
    END IF;

    PERFORM net.http_post(
        url := v_url,
        body := jsonb_build_object(
            'action', 'auto_reply',
            'ticket_id', NEW.ticket_id
        ),
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'x-ticket-ai-secret', COALESCE(v_secret, '')
        ),
        timeout_milliseconds := 8000
    );

    RETURN NEW;
EXCEPTION WHEN OTHERS THEN
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.haven_trigger_ticket_message_ai() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS trg_ticket_message_ai_auto_reply ON public.ticket_messages;
CREATE TRIGGER trg_ticket_message_ai_auto_reply
    AFTER INSERT ON public.ticket_messages
    FOR EACH ROW EXECUTE FUNCTION public.haven_trigger_ticket_message_ai();

CREATE OR REPLACE FUNCTION public.haven_on_staff_profile_pending()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, net, extensions, pg_temp AS $$
DECLARE
    v_cfg RECORD;
    v_payload JSONB;
BEGIN
    IF NEW.status <> 'pending' THEN RETURN NEW; END IF;
    IF TG_OP = 'UPDATE' AND COALESCE(OLD.status, '') = 'pending' THEN RETURN NEW; END IF;

    SELECT * INTO v_cfg FROM public.haven_get_discord_config();
    IF NOT v_cfg.alert_staff THEN RETURN NEW; END IF;

    v_payload := jsonb_build_object(
        'username', 'HavenCraft Alerts',
        'embeds', jsonb_build_array(jsonb_build_object(
            'title', 'New Staff Application — ' || COALESCE(NEW.full_name, 'Applicant'),
            'color', 16102667,
            'fields', jsonb_build_array(
                jsonb_build_object('name', 'Minecraft IGN', 'value', COALESCE(NEW.minecraft_ign, '-'), 'inline', true),
                jsonb_build_object('name', 'Discord',       'value', COALESCE(NEW.discord, '-'), 'inline', true),
                jsonb_build_object('name', 'Role',          'value', COALESCE(NEW.role, 'Pending'), 'inline', true),
                jsonb_build_object('name', 'Email',         'value', COALESCE(NEW.email, '-'), 'inline', false)
            ),
            'footer', jsonb_build_object('text', 'HavenCraft SMP • Admin Dashboard'),
            'timestamp', to_char(COALESCE(NEW.created_at, NOW()) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
        ))
    );

    PERFORM public.haven_send_discord_embed(v_payload);
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.haven_on_staff_profile_pending() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS trg_discord_staff_alert ON public.staff_profiles;
CREATE TRIGGER trg_discord_staff_alert
    AFTER INSERT OR UPDATE ON public.staff_profiles
    FOR EACH ROW EXECUTE FUNCTION public.haven_on_staff_profile_pending();


-- ----------------------------------------------------------------------------------------------------
-- 4.6. Discord Alert Management RPCs (Owner Only)
-- ----------------------------------------------------------------------------------------------------
-- Owner RPC: read config (URL returned masked)
CREATE OR REPLACE FUNCTION public.get_discord_alert_config()
RETURNS TABLE (url_masked TEXT, alert_orders BOOLEAN, alert_tickets BOOLEAN, alert_staff BOOLEAN)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, net, extensions, pg_temp AS $$
DECLARE
    v_cfg RECORD;
BEGIN
    IF NOT public.is_haven_owner() THEN
        RAISE EXCEPTION 'Forbidden: Owner access required.';
    END IF;

    SELECT * INTO v_cfg FROM public.haven_get_discord_config();

    url_masked := CASE
        WHEN v_cfg.webhook_url IS NULL OR v_cfg.webhook_url = '' THEN ''
        ELSE left(v_cfg.webhook_url, 34) || '••••••••' || right(v_cfg.webhook_url, 4)
    END;
    alert_orders := v_cfg.alert_orders;
    alert_tickets := v_cfg.alert_tickets;
    alert_staff := v_cfg.alert_staff;
    RETURN NEXT;
END;
$$;

REVOKE ALL ON FUNCTION public.get_discord_alert_config() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_discord_alert_config() TO authenticated;

-- Owner RPC: save config
--    p_webhook_url semantics: NULL = keep existing, '' = clear,
--    a value containing '••••' = keep existing (masked echo from the UI)
CREATE OR REPLACE FUNCTION public.set_discord_alert_config(
    p_webhook_url TEXT,
    p_orders BOOLEAN,
    p_tickets BOOLEAN,
    p_staff BOOLEAN
)
RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, net, extensions, pg_temp AS $$
BEGIN
    IF NOT public.is_haven_owner() THEN
        RAISE EXCEPTION 'Forbidden: Owner access required.';
    END IF;

    IF p_webhook_url IS NOT NULL AND p_webhook_url <> '' AND p_webhook_url NOT LIKE '%••••%' THEN
        IF p_webhook_url NOT LIKE 'https://discord.com/api/webhooks/%'
           AND p_webhook_url NOT LIKE 'https://discordapp.com/api/webhooks/%' THEN
            RAISE EXCEPTION 'Invalid webhook URL: must be a Discord webhook (https://discord.com/api/webhooks/...).';
        END IF;
        INSERT INTO public.app_secrets (key, value, updated_at)
        VALUES ('discord_webhook_url', p_webhook_url, NOW())
        ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();
    ELSIF p_webhook_url = '' THEN
        DELETE FROM public.app_secrets WHERE key = 'discord_webhook_url';
    END IF;

    INSERT INTO public.app_secrets (key, value, updated_at) VALUES
        ('discord_alert_orders', (p_orders = true)::TEXT, NOW()),
        ('discord_alert_tickets', (p_tickets = true)::TEXT, NOW()),
        ('discord_alert_staff', (p_staff = true)::TEXT, NOW())
    ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();

    RETURN TRUE;
END;
$$;

REVOKE ALL ON FUNCTION public.set_discord_alert_config(TEXT, BOOLEAN, BOOLEAN, BOOLEAN) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.set_discord_alert_config(TEXT, BOOLEAN, BOOLEAN, BOOLEAN) TO authenticated;

-- Owner RPC: send a test alert with the current config
CREATE OR REPLACE FUNCTION public.send_discord_test_alert()
RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, net, extensions, pg_temp AS $$
DECLARE
    v_sent BOOLEAN;
BEGIN
    IF NOT public.is_haven_owner() THEN
        RAISE EXCEPTION 'Forbidden: Owner access required.';
    END IF;

    v_sent := public.haven_send_discord_embed(jsonb_build_object(
        'username', 'HavenCraft Alerts',
        'embeds', jsonb_build_array(jsonb_build_object(
            'title', 'Test Alert — Discord Notifications Active',
            'description', 'If you can read this, HavenCraft admin alerts are wired up correctly.',
            'color', 51350,
            'footer', jsonb_build_object('text', 'HavenCraft SMP • Admin Dashboard'),
            'timestamp', to_char(NOW() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
        ))
    ));

    IF NOT v_sent THEN
        RAISE EXCEPTION 'Webhook URL is not configured yet. Save a valid webhook URL first.';
    END IF;
    RETURN TRUE;
END;
$$;

REVOKE ALL ON FUNCTION public.send_discord_test_alert() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.send_discord_test_alert() TO authenticated;


-- ====================================================================================================
-- SECTION 5: STRICT ROW LEVEL SECURITY (RLS) POLICIES (জিরো বাইপাস সিকিউরিটি পলিসি)
-- ====================================================================================================

-- RLS সক্রিয়করণ (Enforce on all 12 tables)
ALTER TABLE public.staff_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.store_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.server_sales ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.server_expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.shop_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ticket_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.gallery ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.staff_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_secrets ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    pol RECORD;
BEGIN
    -- প্রথমে ১২টি টেবিলের সমস্ত লিগ্যাসি বা ডুপ্লিকেট পলিসি পরিষ্কার করা (Zero OR-Bypass Guarantee)
    FOR pol IN
        SELECT policyname, tablename
        FROM pg_policies
        WHERE schemaname = 'public'
          AND tablename IN (
              'staff_profiles', 'store_orders', 'server_sales', 'server_expenses',
              'shop_items', 'tickets', 'ticket_messages', 'site_settings',
              'gallery', 'audit_logs', 'staff_members', 'app_secrets'
          )
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', pol.policyname, pol.tablename);
    END LOOP;

    -- ডুপ্লিকেট পুরনো ট্রিগার থাকলে মুছে ফেলা (trg_guard_staff এখন এককভাবে সব গার্ড করবে)
    DROP TRIGGER IF EXISTS trg_hvn_guard_staff_profile_escalation ON public.staff_profiles;

    -- ------------------------------------------------------------------------------------------------
    -- RLS 1: public.staff_profiles
    -- ------------------------------------------------------------------------------------------------
    CREATE POLICY "staff_read_self_or_active" ON public.staff_profiles FOR SELECT 
        USING (
            auth.uid() = id
            OR lower(email) = lower(coalesce(auth.jwt() ->> 'email', ''))
            OR public.hvn_can_view('users')
            OR public.is_haven_owner()
        );

    CREATE POLICY "staff_insert_policy" ON public.staff_profiles FOR INSERT 
        WITH CHECK (auth.uid() = id OR public.hvn_can_edit('users') OR public.is_haven_owner());

    CREATE POLICY "staff_update_policy" ON public.staff_profiles FOR UPDATE 
        USING (auth.uid() = id OR public.hvn_can_edit('users') OR public.is_haven_owner())
        WITH CHECK (auth.uid() = id OR public.hvn_can_edit('users') OR public.is_haven_owner());

    CREATE POLICY "staff_delete_owner" ON public.staff_profiles FOR DELETE 
        USING (public.is_haven_owner() OR public.hvn_can_edit('users'));

    -- ------------------------------------------------------------------------------------------------
    -- RLS 2: public.store_orders (View requires 'orders', Write requires 'orders_edit' or Owner)
    -- ------------------------------------------------------------------------------------------------
    CREATE POLICY "orders_insert_public" ON public.store_orders FOR INSERT 
        WITH CHECK (true); -- পাবলিক ওয়েবসাইট থেকে প্লেয়াররা অর্ডার সাবমিট করতে পারবে

    CREATE POLICY "orders_select_staff" ON public.store_orders FOR SELECT 
        USING (public.has_haven_perm('orders') OR public.is_haven_owner());

    CREATE POLICY "orders_update_staff" ON public.store_orders FOR UPDATE 
        USING (public.hvn_can_edit('orders') OR public.is_haven_owner())
        WITH CHECK (public.hvn_can_edit('orders') OR public.is_haven_owner());

    CREATE POLICY "orders_delete_staff" ON public.store_orders FOR DELETE 
        USING (public.hvn_can_edit('orders') OR public.is_haven_owner());

    -- ------------------------------------------------------------------------------------------------
    -- RLS 3: public.server_sales (View requires 'finance', Write requires 'finance_edit' or Owner)
    -- ------------------------------------------------------------------------------------------------
    CREATE POLICY "sales_select_staff" ON public.server_sales FOR SELECT 
        USING (public.has_haven_perm('finance') OR public.is_haven_owner());

    CREATE POLICY "sales_manage_staff" ON public.server_sales FOR ALL 
        USING (public.hvn_can_edit('finance') OR public.is_haven_owner()) 
        WITH CHECK (public.hvn_can_edit('finance') OR public.is_haven_owner());

    -- ------------------------------------------------------------------------------------------------
    -- RLS 4: public.server_expenses (View requires 'finance', Write requires 'finance_edit' or Owner)
    -- ------------------------------------------------------------------------------------------------
    CREATE POLICY "expenses_select_staff" ON public.server_expenses FOR SELECT 
        USING (public.has_haven_perm('finance') OR public.is_haven_owner());

    CREATE POLICY "expenses_manage_staff" ON public.server_expenses FOR ALL 
        USING (public.hvn_can_edit('finance') OR public.is_haven_owner()) 
        WITH CHECK (public.hvn_can_edit('finance') OR public.is_haven_owner());

    -- ------------------------------------------------------------------------------------------------
    -- RLS 5: public.shop_items (Strictly requires 'shop_edit' permission for modification)
    -- ------------------------------------------------------------------------------------------------
    CREATE POLICY "Public can view shop_items" ON public.shop_items FOR SELECT 
        USING (true); -- সবাই শপ আইটেমস ও দাম দেখতে পারবে

    CREATE POLICY "Staff can edit shop_items" ON public.shop_items FOR ALL 
        USING (public.hvn_can_edit('shop') OR public.is_haven_owner()) 
        WITH CHECK (public.hvn_can_edit('shop') OR public.is_haven_owner());

    -- ------------------------------------------------------------------------------------------------
    -- RLS 6: public.tickets (View requires 'tickets', Write requires 'tickets_edit' or Owner)
    -- ------------------------------------------------------------------------------------------------
    CREATE POLICY "tickets_insert_public" ON public.tickets FOR INSERT WITH CHECK (true);

    CREATE POLICY "tickets_select_staff" ON public.tickets FOR SELECT 
        USING (public.has_haven_perm('tickets') OR public.is_haven_owner());

    CREATE POLICY "tickets_update_staff" ON public.tickets FOR UPDATE 
        USING (public.hvn_can_edit('tickets') OR public.is_haven_owner())
        WITH CHECK (public.hvn_can_edit('tickets') OR public.is_haven_owner());

    CREATE POLICY "tickets_delete_staff" ON public.tickets FOR DELETE 
        USING (public.hvn_can_edit('tickets') OR public.is_haven_owner());

    -- ------------------------------------------------------------------------------------------------
    -- RLS 7: public.ticket_messages (Public read for player chat realtime + Staff permissions)
    -- ------------------------------------------------------------------------------------------------
    CREATE POLICY "ticket_msgs_select_public" ON public.ticket_messages FOR SELECT 
        USING (true);

    CREATE POLICY "ticket_msgs_insert" ON public.ticket_messages FOR INSERT 
        WITH CHECK (
            (sender = 'player') OR 
            ((sender = 'staff' OR sender = 'system') AND (public.hvn_can_edit('tickets') OR public.is_haven_owner()))
        );

    CREATE POLICY "ticket_msgs_delete_staff" ON public.ticket_messages FOR DELETE 
        USING (public.hvn_can_edit('tickets') OR public.is_haven_owner());

    -- ------------------------------------------------------------------------------------------------
    -- RLS 8: public.site_settings
    -- ------------------------------------------------------------------------------------------------
    CREATE POLICY "settings_public_read" ON public.site_settings FOR SELECT USING (true);

    CREATE POLICY "settings_write_staff" ON public.site_settings FOR ALL 
        USING (
            (key = 'shop_catalog' AND (public.hvn_can_edit('shop') OR public.is_haven_owner())) OR 
            (key <> 'shop_catalog' AND (public.hvn_can_edit('settings') OR public.is_haven_owner()))
        )
        WITH CHECK (
            (key = 'shop_catalog' AND (public.hvn_can_edit('shop') OR public.is_haven_owner())) OR 
            (key <> 'shop_catalog' AND (public.hvn_can_edit('settings') OR public.is_haven_owner()))
        );

    -- ------------------------------------------------------------------------------------------------
    -- RLS 9: public.gallery (Strictly requires 'gallery_edit' permission for staff actions)
    -- ------------------------------------------------------------------------------------------------
    -- ১. যে কেউ (ভিজিটর ও প্লেয়ার) অনুমোদিত গ্যালারি ছবি দেখতে পারবে
    CREATE POLICY "gallery_read_public" ON public.gallery FOR SELECT 
        USING (true);

    -- ২. সাধারণ প্লেয়াররা শুধুমাত্র 'submissions' হিসেবে স্ক্রিনশট রিভিউয়ের জন্য সাবমিট করতে পারবে
    CREATE POLICY "gallery_submit_public" ON public.gallery FOR INSERT 
        WITH CHECK (status = 'submissions');

    -- ৩. এডিট পারমিশনধারী স্টাফ বা ওনার সরাসরি অ্যাপ্রুভড ছবি পোস্ট করতে পারবে
    CREATE POLICY "gallery_staff_insert" ON public.gallery FOR INSERT 
        WITH CHECK (public.hvn_can_edit('gallery') OR public.is_haven_owner());

    -- ৪. এডিট পারমিশনধারী স্টাফ বা ওনার ছবি অ্যাপ্রুভ, ক্যাটাগরি এডিট বা ফিচার করতে পারবে
    CREATE POLICY "gallery_staff_update" ON public.gallery FOR UPDATE 
        USING (public.hvn_can_edit('gallery') OR public.is_haven_owner())
        WITH CHECK (public.hvn_can_edit('gallery') OR public.is_haven_owner());

    -- ৫. এডিট পারমিশনধারী স্টাফ বা ওনার অনুপযুক্ত ছবি মুছে ফেলতে পারবে
    CREATE POLICY "gallery_staff_delete" ON public.gallery FOR DELETE 
        USING (public.hvn_can_edit('gallery') OR public.is_haven_owner());

    -- ------------------------------------------------------------------------------------------------
    -- RLS 10: public.audit_logs (Only active staff can insert; 'logs' perm or Owner to read)
    -- ------------------------------------------------------------------------------------------------
    CREATE POLICY "logs_insert_staff" ON public.audit_logs FOR INSERT 
        WITH CHECK (public.has_haven_perm('any'));

    CREATE POLICY "logs_read_staff" ON public.audit_logs FOR SELECT 
        USING (public.has_haven_perm('logs') OR public.is_haven_owner());

    CREATE POLICY "logs_delete_owner" ON public.audit_logs FOR DELETE 
        USING (public.is_haven_owner());

    -- ------------------------------------------------------------------------------------------------
    -- RLS 11: public.staff_members (Strictly requires 'users_edit' permission or Owner)
    -- ------------------------------------------------------------------------------------------------
    CREATE POLICY "staff_members_read_public" ON public.staff_members FOR SELECT 
        USING (true);

    CREATE POLICY "staff_members_manage_staff" ON public.staff_members FOR ALL 
        USING (public.hvn_can_edit('users') OR public.is_haven_owner())
        WITH CHECK (public.hvn_can_edit('users') OR public.is_haven_owner());

    -- ------------------------------------------------------------------------------------------------
    -- RLS 12: public.app_secrets (Zero policies: default deny, accessible ONLY via Owner RPCs)
    -- ------------------------------------------------------------------------------------------------
    REVOKE ALL ON public.app_secrets FROM anon, authenticated;

    -- ------------------------------------------------------------------------------------------------
    -- STORAGE: gallery & ticket-attachments buckets & security policies (২ মেগাবাইট ফ্রি-প্ল্যান লিমিট ও ফোল্ডার লিস্টিং সুরক্ষা)
    -- ------------------------------------------------------------------------------------------------
    INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
    VALUES ('gallery', 'gallery', true, 2097152, ARRAY['image/webp', 'image/png', 'image/jpeg', 'image/gif'])
    ON CONFLICT (id) DO UPDATE SET
        public = true,
        file_size_limit = 2097152,
        allowed_mime_types = ARRAY['image/webp', 'image/png', 'image/jpeg', 'image/gif'];

    INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
    VALUES ('ticket-attachments', 'ticket-attachments', true, 2097152, ARRAY['image/webp', 'image/png', 'image/jpeg', 'image/gif', 'audio/webm', 'audio/ogg', 'audio/mp4', 'audio/mpeg'])
    ON CONFLICT (id) DO UPDATE SET
        public = true,
        file_size_limit = 2097152,
        allowed_mime_types = ARRAY['image/webp', 'image/png', 'image/jpeg', 'image/gif', 'audio/webm', 'audio/ogg', 'audio/mp4', 'audio/mpeg'];

    DROP POLICY IF EXISTS "Public can view gallery images" ON storage.objects;
    DROP POLICY IF EXISTS "Authenticated staff can upload gallery images" ON storage.objects;
    DROP POLICY IF EXISTS "Authenticated staff can update gallery images" ON storage.objects;
    DROP POLICY IF EXISTS "Authenticated staff can delete gallery images" ON storage.objects;

    -- ১. সাধারণ ভিজিটর শুধু গ্যালারির পাবলিক ছবি লিস্ট/দেখতে পারবে (tickets/ ফোল্ডার বা ticket-attachments লিস্টিং শুধুমাত্র টিকেট স্টাফ ও ওনারের জন্য)
    CREATE POLICY "Public can view gallery images" ON storage.objects FOR SELECT 
        USING (
            (bucket_id = 'gallery' AND name NOT LIKE 'tickets/%')
            OR (bucket_id IN ('gallery', 'ticket-attachments') AND (public.hvn_can_view('tickets') OR public.is_haven_owner()))
        );

    -- ২. শুধুমাত্র গ্যালারি এডিট স্টাফ গ্যালারিতে এবং টিকেট এডিট স্টাফ ticket-attachments বাকেটে আপলোড করতে পারবে
    CREATE POLICY "Authenticated staff can upload gallery images" ON storage.objects FOR INSERT 
        WITH CHECK (
            (bucket_id = 'gallery' AND name NOT LIKE 'tickets/%' AND (public.hvn_can_edit('gallery') OR public.is_haven_owner()))
            OR (bucket_id = 'ticket-attachments' AND (public.hvn_can_edit('tickets') OR public.is_haven_owner()))
        );

    -- ৩. শুধুমাত্র গ্যালারি এডিট স্টাফ গ্যালারিতে এবং টিকেট এডিট স্টাফ ticket-attachments বাকেটে আপডেট করতে পারবে
    CREATE POLICY "Authenticated staff can update gallery images" ON storage.objects FOR UPDATE 
        USING (
            (bucket_id = 'gallery' AND name NOT LIKE 'tickets/%' AND (public.hvn_can_edit('gallery') OR public.is_haven_owner()))
            OR (bucket_id = 'ticket-attachments' AND (public.hvn_can_edit('tickets') OR public.is_haven_owner()))
        )
        WITH CHECK (
            (bucket_id = 'gallery' AND name NOT LIKE 'tickets/%' AND (public.hvn_can_edit('gallery') OR public.is_haven_owner()))
            OR (bucket_id = 'ticket-attachments' AND (public.hvn_can_edit('tickets') OR public.is_haven_owner()))
        );

    -- ৪. শুধুমাত্র এডিট পারমিশনধারী স্টাফ বা ওনার স্টোরেজ থেকে ফাইল ডিলিট করতে পারবে
    CREATE POLICY "Authenticated staff can delete gallery images" ON storage.objects FOR DELETE 
        USING (
            (bucket_id = 'gallery' AND (public.hvn_can_edit('gallery') OR public.is_haven_owner()))
            OR (bucket_id = 'ticket-attachments' AND (public.hvn_can_edit('tickets') OR public.is_haven_owner()))
        );
END
$$;

-- ====================================================================================================
-- SECTION 5.5: SUPABASE REALTIME PUBLICATIONS (লাইভ নোটিফিকেশন ও পুশ ব্রডকাস্ট)
-- ====================================================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        BEGIN
            ALTER PUBLICATION supabase_realtime ADD TABLE public.store_orders;
        EXCEPTION WHEN duplicate_object THEN NULL;
        END;
        BEGIN
            ALTER PUBLICATION supabase_realtime ADD TABLE public.tickets;
        EXCEPTION WHEN duplicate_object THEN NULL;
        END;
        BEGIN
            ALTER PUBLICATION supabase_realtime ADD TABLE public.staff_profiles;
        EXCEPTION WHEN duplicate_object THEN NULL;
        END;
        BEGIN
            ALTER PUBLICATION supabase_realtime ADD TABLE public.ticket_messages;
        EXCEPTION WHEN duplicate_object THEN NULL;
        END;
    END IF;
END $$;


-- ====================================================================================================
-- SECTION 6: MASTER SEED DATA (প্রয়োজনীয় প্রাথমিক ও লাইভ ডাটা রেকর্ড)
-- ====================================================================================================

-- ----------------------------------------------------------------------------------------------------
-- ১. সার্ভার ওনার নিশ্চিতকরণ (Owner Guarantee: rajuel55@gmail.com / JU3L_ with finance:true)
-- ----------------------------------------------------------------------------------------------------
UPDATE public.staff_profiles 
SET 
    full_name = 'Juel',
    minecraft_ign = 'JU3L_',
    role = 'Owner',
    status = 'active',
    is_active = true,
    permissions = '{"users":true,"orders":true,"finance":true,"console":true,"settings":true,"tickets":true,"shop":true,"gallery":true,"logs":true}'::jsonb
WHERE lower(email) = 'rajuel55@gmail.com';

INSERT INTO public.staff_profiles (id, email, full_name, minecraft_ign, role, status, is_active, permissions)
SELECT 
    id, 
    email, 
    'Juel', 
    'JU3L_', 
    'Owner', 
    'active', 
    true, 
    '{"users":true,"orders":true,"finance":true,"console":true,"settings":true,"tickets":true,"shop":true,"gallery":true,"logs":true}'::jsonb
FROM auth.users
WHERE lower(email) = 'rajuel55@gmail.com'
ON CONFLICT (id) DO UPDATE SET
    role = 'Owner',
    status = 'active',
    is_active = true,
    permissions = '{"users":true,"orders":true,"finance":true,"console":true,"settings":true,"tickets":true,"shop":true,"gallery":true,"logs":true}'::jsonb;


-- ----------------------------------------------------------------------------------------------------
-- ২. অফিসিয়াল শপ আইটেমস ও কনসোল কমান্ডস (Official 22 Items: Ranks & Spawners)
-- ----------------------------------------------------------------------------------------------------
INSERT INTO public.shop_items (id, type, name, category, bdt, usd, command, perks, active, icon, icon_color)
VALUES
-- Ranks (With Structured JSON Perks, Items, Spawner Quotas & Commands)
('rank-prime', 'rank', 'Prime Rank', 'Starter', 220.00, 1.99, 'lp user {username} parent set prime', '{"summary":"8 Perks • 5 Cmds","emoji":"💎","badge":"Starter","popular":false,"popularBadgeText":"","spawnerQuotas":{"basic":2,"combat":2,"advanced":0,"special":0},"perksList":[{"icon":"💸","text":"In-game Money: <strong>200K</strong>"},{"icon":"✨","text":"In-game Shard: <strong>400</strong>"},{"icon":"🏠","text":"<strong>8</strong> SetHomes"},{"icon":"🛡️","text":"Max Armor & Tools <em>(Vanilla Max)</em>"},{"icon":"📦","text":"<strong>8</strong> Shulker Boxes"},{"icon":"🍏","text":"90 Golden Apple + 12 Enchanted Golden Apple"},{"icon":"🐄","text":"4 Spawners <em>(Basic×2 & Combat×2)</em>"},{"icon":"🧱","text":"<strong>20K</strong> Claim Blocks"}],"commandsInherit":"","commandsList":["/back","/ptime","/disposal","/near","/hat"]}', true, 'bi-award-fill', '#60a5fa'),
('rank-legend', 'rank', 'Legend Rank', 'Mid-Tier', 340.00, 2.99, 'lp user {username} parent set legend', '{"summary":"9 Perks • 7 Cmds","emoji":"⚔️","badge":"Mid-Tier","popular":false,"popularBadgeText":"","spawnerQuotas":{"basic":2,"combat":4,"advanced":0,"special":0},"perksList":[{"icon":"💸","text":"In-game Money: <strong>350K</strong>"},{"icon":"✨","text":"In-game Shard: <strong>600</strong>"},{"icon":"🏠","text":"<strong>10</strong> SetHomes"},{"icon":"⚔️","text":"Full Max + Custom Gear <em>(Limited)</em>"},{"icon":"⚔️","text":"Full Max Custom Sword"},{"icon":"📦","text":"<strong>12</strong> Shulker Boxes"},{"icon":"🍏","text":"160 Golden Apple + 32 Enchanted Golden Apple"},{"icon":"🐓","text":"6 Spawners <em>(Basic×2 & Combat×4)</em>"},{"icon":"🧱","text":"<strong>30K</strong> Claim Blocks"}],"commandsInherit":"All Prime Commands +","commandsList":["/workbench","/echest","/feed","/nick","/smithingtable","/cartographytable","/loom"]}', true, 'bi-star-fill', '#eab308'),
('rank-elite', 'rank', 'Elite Rank', 'Best Value', 420.00, 3.69, 'lp user {username} parent set elite', '{"summary":"11 Perks • 5 Cmds","emoji":"👑","badge":"Best Value","popular":true,"popularBadgeText":"Most Popular","spawnerQuotas":{"basic":2,"combat":4,"advanced":2,"special":0},"perksList":[{"icon":"💸","text":"In-game Money: <strong>500K</strong>"},{"icon":"✨","text":"In-game Shard: <strong>600</strong>"},{"icon":"🏠","text":"<strong>12</strong> SetHomes"},{"icon":"🐦","text":"<strong>1</strong> Elytra"},{"icon":"🔨","text":"Max Mace ×1"},{"icon":"⛏","text":"3x3 Custom Pickaxe"},{"icon":"⚔️","text":"Full Max-Custom Gear"},{"icon":"📦","text":"<strong>15</strong> Shulker Boxes"},{"icon":"🍏","text":"256 Golden Apple + 48 Enchanted Golden Apple"},{"icon":"🐄","text":"8 Spawners <em>(Basic×2, Advance×2 & Combat×4)</em>"},{"icon":"🧱","text":"<strong>40K</strong> Claim Blocks"}],"commandsInherit":"All Legend Commands +","commandsList":["/heal","/ext","/ptime night","/anvil","/grindstone"]}', true, 'bi-shield-shaded', '#ef4444'),

-- Basic Spawners (৳25)
('spawner-cow', 'spawner', 'Cow Spawner', 'Basic', 25.00, 0.25, 'ss give {username} smart_spawner cow_spawner 1', 'Leather & Beef Farm', true, 'bi-box-seam', '#a8a29e'),
('spawner-pig', 'spawner', 'Pig Spawner', 'Basic', 25.00, 0.25, 'ss give {username} smart_spawner pig_spawner 1', 'Food Supply Farm', true, 'bi-box-seam', '#ec4899'),
('spawner-sheep', 'spawner', 'Sheep Spawner', 'Basic', 25.00, 0.25, 'ss give {username} smart_spawner sheep_spawner 1', 'Wool & Mutton Supply', true, 'bi-box-seam', '#e2e8f0'),
('spawner-chicken', 'spawner', 'Chicken Spawner', 'Basic', 25.00, 0.25, 'ss give {username} smart_spawner chicken_spawner 1', 'Feather & Egg Supply', true, 'bi-box-seam', '#fde047'),

-- Combat Spawners (৳35)
('spawner-zombie', 'spawner', 'Zombie Spawner', 'Combat', 35.00, 0.32, 'ss give {username} smart_spawner zombie_spawner 1', 'XP & Rotten Flesh Farm', true, 'bi-virus', '#4ade80'),
('spawner-spider', 'spawner', 'Spider Spawner', 'Combat', 35.00, 0.32, 'ss give {username} smart_spawner spider_spawner 1', 'String & Spider Eye Farm', true, 'bi-bug-fill', '#dc2626'),
('spawner-cavespider', 'spawner', 'Cave Spider Spawner', 'Combat', 35.00, 0.32, 'ss give {username} smart_spawner cave_spider_spawner 1', 'Compact XP Grinder', true, 'bi-bug-fill', '#0284c7'),
('spawner-skeleton', 'spawner', 'Skeleton Spawner', 'Combat', 35.00, 0.32, 'ss give {username} smart_spawner skeleton_spawner 1', 'Bone & Arrow Supply', true, 'bi-skull-fill', '#cbd5e1'),
('spawner-guardian', 'spawner', 'Guardian Spawner', 'Combat', 35.00, 0.32, 'ss give {username} smart_spawner guardian_spawner 1', 'Prismarine & Shard Farm', true, 'bi-water', '#06b6d4'),

-- Advanced Spawners (৳45)
('spawner-enderman', 'spawner', 'Enderman Spawner', 'Advanced', 45.00, 0.40, 'ss give {username} smart_spawner enderman_spawner 1', 'Ender Pearl & XP Farm', true, 'bi-circle-fill', '#a855f7'),
('spawner-slime', 'spawner', 'Slime Spawner', 'Advanced', 45.00, 0.40, 'ss give {username} smart_spawner slime_spawner 1', 'Slime Ball Auto Farm', true, 'bi-square-fill', '#84cc16'),
('spawner-magmacube', 'spawner', 'Magma Cube Spawner', 'Advanced', 45.00, 0.40, 'ss give {username} smart_spawner magma_cube_spawner 1', 'Magma Cream & Froglight', true, 'bi-fire', '#ea580c'),
('spawner-creeper', 'spawner', 'Creeper Spawner', 'Advanced', 45.00, 0.40, 'ss give {username} smart_spawner creeper_spawner 1', 'Gunpowder & TNT Supply', true, 'bi-exclamation-triangle-fill', '#22c55e'),
('spawner-blaze', 'spawner', 'Blaze Spawner', 'Advanced', 45.00, 0.40, 'ss give {username} smart_spawner blaze_spawner 1', 'Blaze Rod & Fuel Farm', true, 'bi-fire', '#f97316'),
('spawner-piglin', 'spawner', 'Zombified Piglin Spawner', 'Advanced', 45.00, 0.40, 'ss give {username} smart_spawner zombified_piglin_spawner 1', 'Automated Gold Farming', true, 'bi-coin', '#eab308'),

-- Special Spawners (৳55)
('spawner-irongolem', 'spawner', 'Iron Golem Spawner', 'Special', 55.00, 0.50, 'ss give {username} smart_spawner iron_golem_spawner 1', 'Automated Iron Ingot Farm', true, 'bi-shield-fill-plus', '#94a3b8'),
('spawner-ghast', 'spawner', 'Ghast Spawner', 'Special', 55.00, 0.50, 'ss give {username} smart_spawner ghast_spawner 1', 'Ghast Tears & Gunpowder', true, 'bi-cloud-fill', '#f8fafc'),
('spawner-breeze', 'spawner', 'Breeze Spawner', 'Special', 55.00, 0.50, 'ss give {username} smart_spawner breeze_spawner 1', 'Breeze Rod & Wind Charges', true, 'bi-wind', '#38bdf8'),
('spawner-witherskeleton', 'spawner', 'Wither Skeleton Spawner', 'Special', 55.00, 0.50, 'ss give {username} smart_spawner wither_skeleton_spawner 1', 'Wither Skulls for Beacons', true, 'bi-skull', '#475569')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    category = EXCLUDED.category,
    bdt = EXCLUDED.bdt,
    usd = EXCLUDED.usd,
    command = EXCLUDED.command,
    perks = EXCLUDED.perks,
    active = EXCLUDED.active,
    icon = EXCLUDED.icon,
    icon_color = EXCLUDED.icon_color,
    updated_at = NOW();


-- ----------------------------------------------------------------------------------------------------
-- ৩. অপারেশনাল সেলস ও এক্সপেন্স রেকর্ড (Operational Ledger - Seasonal Data)
-- ----------------------------------------------------------------------------------------------------
-- নোট: সেলস ও অপারেটিং খরচের ট্রানজ্যাকশন ডাটা সিজনাল হওয়ায় মাস্টার স্কিমাতে হার্ডকোড করা নেই।
-- নতুন সিজন শুরু বা ডাটাবেজ রিসেট করার সময় এগুলো ফ্রেশ ও খালি থাকে যাতে সিজনাল কনফ্লিক্ট না হয়।
-- প্রয়োজন হলে historical_sales_seeds.sql ফাইলটি রান করে বিগত সিজনের ৩৬টি রেকর্ড লোড করতে পারেন।


-- ----------------------------------------------------------------------------------------------------
-- ৪. ডিফল্ট সাইট সেটিংস (Default Central Site Settings)
-- ----------------------------------------------------------------------------------------------------
INSERT INTO public.site_settings (key, value)
VALUES
('server_info', '{
    "serverIp": "mc.havencraft.pro",
    "port": "2566",
    "bedrockPort": "2025",
    "javaPort": "2566",
    "discordUrl": "https://dc.havencraft.pro",
    "seasonName": "Season 6",
    "websiteUrl": "https://havencraft.pro"
}'::jsonb)
ON CONFLICT (key) DO UPDATE SET
    value = EXCLUDED.value,
    updated_at = NOW();



-- ----------------------------------------------------------------------------------------------------
-- ৫. কমিউনিটি গ্যালারি মিডিয়া সীড (44 Legacy Community WebP Screenshots)
-- ----------------------------------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS idx_gallery_image_url ON public.gallery (image_url);

INSERT INTO public.gallery (image_url, caption, category, uploaded_by, featured, status, created_at)
VALUES
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779830687590_j74pgfjgc.webp', 'Player Build', 'Player Builds', 'JU3L_', true, 'approved', '2026-05-26T21:24:55.899134+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780261982319_2nzzr56e8.webp', 'Enderman', 'SMP Community', 'Juel', true, 'approved', '2026-05-31T21:13:06.246438+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779712960866_wp989hhnk.webp', 'Beautiful Build', 'Player Builds', 'Staff', false, 'approved', '2026-05-25T12:42:58.194188+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779713605428_aj6gky58c.webp', 'Kill this player guys', 'SMP Community', 'Staff', false, 'approved', '2026-05-25T12:53:27.470982+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779714087346_3lk894oca.webp', 'SaimongamerZ''s Build', 'Player Builds', 'Staff', false, 'approved', '2026-05-25T13:01:39.734549+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779716007365_rb270xpwz.webp', 'Crafting a brand new day. ☀️', 'SMP Community', 'Juel', true, 'approved', '2026-05-25T13:34:05.306856+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779722903445_6l6mqkdgz.webp', '💛', 'SMP Community', 'Rafi_PRX', false, 'approved', '2026-05-25T15:28:31.158219+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779722930795_ur4ahh6ej.webp', '💛', 'SMP Community', 'Rafi_PRX', false, 'approved', '2026-05-25T15:28:52.40851+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779723088027_14ej182we.webp', 'HavenCraft''s second largest build', 'Player Builds', 'Rafi_PRX', false, 'approved', '2026-05-25T15:31:30.221586+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779723167078_1w271918q.webp', 'Monster Infinity Castle', 'SMP Community', 'Rafi_PRX', false, 'approved', '2026-05-25T15:32:49.149895+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779723242902_ejw8nfq09.webp', 'Player Build', 'Player Builds', 'JU3L_', true, 'approved', '2026-05-25T15:34:18.759349+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779728249072_oro3duw9l.webp', 'HavenCraft''s richest player base', 'SMP Community', 'Rafi_PRX', false, 'approved', '2026-05-25T16:57:31.145442+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779728284120_ct59j848m.webp', 'HavenCraft''s richest player', 'SMP Community', 'Rafi_PRX', false, 'approved', '2026-05-25T16:58:06.317621+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779728303713_mt3blu935.webp', 'What do you think about this?', 'SMP Community', 'Rafi_PRX', false, 'approved', '2026-05-25T16:58:24.979759+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779728561846_v939e4gak.webp', 'Haven Craft Haat', 'SMP Community', 'Rafi_PRX', false, 'approved', '2026-05-25T17:02:44.194676+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779829690891_oqh53frvm.webp', 'Spawn', 'Spawn Hub', 'JU3L_', true, 'approved', '2026-05-26T21:08:24.579774+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779830782310_stqlm8mz8.webp', 'Beautiful  Build', 'Player Builds', 'JU3L_', true, 'approved', '2026-05-26T21:26:37.096399+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779834043400_ed2tls1io.webp', 'Nether Sword', 'PvP Arena', 'JU3L_', true, 'approved', '2026-05-26T22:20:51.990126+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1779834090020_si9vkyrku.webp', 'Modern Build', 'Player Builds', 'JU3L_', true, 'approved', '2026-05-26T22:21:36.029094+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780260838369_equpzkzru.webp', 'View....', 'SMP Community', 'Juel', true, 'approved', '2026-05-31T20:54:04.501905+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780507789918_jg6q4qcao.webp', 'Faah', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-03T17:29:55.129294+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780507843071_5amthwfcw.webp', 'Officer on duty too Photoshoot 😂', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-03T17:30:45.952493+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780507870868_2vtfnfmxk.webp', 'Legends never die?', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-03T17:31:13.801227+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780507925099_yw6aytxmy.webp', 'Where are you Zoro?', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-03T17:32:09.381205+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780507977785_1k6fvhwy2.webp', 'Tower of blue ice', 'Player Builds', 'Rafy_PRX', false, 'approved', '2026-06-03T17:33:00.80978+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780508057336_qqifkju0o.webp', 'Who said legends die?', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-03T17:34:19.824642+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780510780853_gfcu7t0es.webp', 'Views from the top', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-03T18:19:44.804641+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780510806229_uok7nxmt4.webp', 'Watching over the fortress', 'Player Builds', 'Rafy_PRX', false, 'approved', '2026-06-03T18:20:08.874368+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780510823359_bh5a2w2bi.webp', 'Just king things.', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-03T18:20:25.95476+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780580874742_dmd0j7nsl.webp', 'Coastal living at its absolute finest. 🏖️', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-04T13:47:59.80674+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780580904611_x3iu98f1n.webp', 'Lost in a wonderland of giant spores and endless skies. 🍄✨', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-04T13:48:27.434277+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780580938100_ihlfas9ac.webp', 'Shaders + Cherry Blossom Biome village = Perfection. 🏠���', 'Player Builds', 'Rafy_PRX', false, 'approved', '2026-06-04T13:49:02.278655+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780580959662_dn9rqhk5z.webp', 'Hidden in the clouds. ✨', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-04T13:49:23.248243+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780589146056_5cuh75xhv.webp', 'Eid day', 'Boss Event', 'Rafy_PRX', false, 'approved', '2026-06-04T16:05:50.419641+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780589197846_cfjjmiw6m.webp', 'Faah', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-04T16:06:40.637829+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780589272119_guoki1pum.webp', 'True beauty of Haven Craft', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-04T16:07:54.824655+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780589300687_v8yigjnta.webp', '<3', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-04T16:08:22.632625+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780589331507_467l4ctpo.webp', 'Legend....', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-04T16:08:53.356191+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780589349119_zhzhpxwix.webp', '😭', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-04T16:09:10.810152+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1781124706613_oferxcdad.webp', 'sun 🌞', 'SMP Community', 'Juel', true, 'approved', '2026-06-10T20:52:00.279433+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1780508111980_hh75nsb3w.webp', 'Team Tempest Owner and Admin 🗿', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-03T17:35:14.594118+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1781988927568_kfy0dz5uj.webp', '🐝', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-20T20:55:31.796234+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1781988957697_xlvnz3kyy.webp', '🐝', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-20T20:55:59.970989+00:00'),
  ('https://rbrrkphtobxcmnzshnqj.supabase.co/storage/v1/object/public/gallery/1781988978492_x2ilxb2r6.webp', '🐝', 'SMP Community', 'Rafy_PRX', false, 'approved', '2026-06-20T20:56:20.547677+00:00')
ON CONFLICT (image_url) DO UPDATE SET
    caption = EXCLUDED.caption,
    category = EXCLUDED.category,
    uploaded_by = EXCLUDED.uploaded_by,
    featured = EXCLUDED.featured,
    status = 'approved';


-- ----------------------------------------------------------------------------------------------------
-- ৬. পাবলিক ওয়েবসাইট স্টাফ টিম সীড (7 Initial Server Staff Members)
-- ----------------------------------------------------------------------------------------------------
INSERT INTO public.staff_members (name, category, role, role_class, discord, description, skin, order_index, is_active)
VALUES
('JU3L_', 'Management', 'Owner', 'role-owner', 'ju3l_', 'Founder and lead developer of HavenCraft. Ensures everything runs smoothly and the community thrives.', '', 1, true),
('Zipp_404', 'Management', 'Admin', 'role-admin', 'zipp_404', 'Manages the server infrastructure, technical optimization, and backend stability.', '', 2, true),
('.Rafi_PRX', 'Management', 'Admin', 'role-admin', 'rafi_prx', 'Oversees community management, player support, and in-game events.', 'https://textures.minecraft.net/texture/3a5b39537e96566e8df214cd2b5c94fafbc37733bc98d9eb916e4ed814ad06e4', 3, true),
('SaimongamerZ', 'Management', 'Admin', 'role-admin', 'saimongamerz', 'Oversees community management, player support, and in-game events.', '', 4, true),
('Mrdevil1x', 'Moderation', 'Moderator', 'role-mod', 'Mrdevil1x', 'Vigilant community guardian who monitors chat and mediates player conflicts with fairness. Known for organizing community build events.', 'https://textures.minecraft.net/texture/2f38c0df84ca184fb127ff183ac720ac5447dfeb9dbb791b235dcba506bd56a2', 5, true),
('IM_SAM_JI', 'Support', 'Officer', 'role-officer', 'IM_SAM_JI', 'Dedicated support officer helping newcomers settle in and assisting players with tickets and queries.', '', 6, true),
('NOT_SALMAN', 'Support', 'Officer', 'role-officer', '', 'Dedicated support officer helping newcomers settle in and assisting players with tickets and queries.', 'https://textures.minecraft.net/texture/a09e60ab4d321482408fbcd19aeb54284402847998114eddff9c3037a389d7fa', 7, true)
ON CONFLICT (lower(name)) DO UPDATE SET
    category = EXCLUDED.category,
    role = EXCLUDED.role,
    role_class = EXCLUDED.role_class,
    discord = EXCLUDED.discord,
    description = EXCLUDED.description,
    skin = EXCLUDED.skin,
    order_index = EXCLUDED.order_index,
    is_active = EXCLUDED.is_active;

-- ====================================================================================================
-- SECTION 7: ONE-CLICK VERIFICATION DIAGNOSTIC (এক ক্লিকে ডাটাবেজ স্থিতি দেখার কুয়েরি)
-- ====================================================================================================
--
-- যেকোনো সময় Supabase SQL Editor-এ নিচের কুয়েরিটি রান করলে সমস্ত ১০টি টেবিল, কলাম সংখ্যা,
-- রো সংখ্যা এবং RLS স্ট্যাটাস ১ সেকেন্ডে স্ক্রিনে সামারি হিসেবে দেখা যাবে:
--
/*
SELECT 
    t.table_name,
    COUNT(c.column_name) AS total_columns,
    COALESCE(s.row_estimate, 0) AS approximate_rows,
    CASE WHEN p.tablename IS NOT NULL THEN 'ENABLED (Protected)' ELSE 'DISABLED (Warning)' END AS rls_status
FROM information_schema.tables t
JOIN information_schema.columns c ON c.table_name = t.table_name AND c.table_schema = 'public'
LEFT JOIN (
    SELECT relname AS table_name, reltuples::bigint AS row_estimate
    FROM pg_class
    WHERE relnamespace = 'public'::regnamespace
) s ON s.table_name = t.table_name
LEFT JOIN (
    SELECT DISTINCT tablename FROM pg_tables WHERE schemaname = 'public' AND rowsecurity = true
) p ON p.tablename = t.table_name
WHERE t.table_schema = 'public' AND t.table_type = 'BASE TABLE'
GROUP BY t.table_name, s.row_estimate, p.tablename
ORDER BY t.table_name ASC;
*/
-- ====================================================================================================
-- সম্পন্ন: HAVENCRAFT SMP MASTER DATABASE SCHEMA v3.6 ACTIVE & READY!
-- ====================================================================================================
