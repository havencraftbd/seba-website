# HavenSync — Supabase Setup Guide

Complete step-by-step guide to set up your Supabase database for HavenSync.  
HavenSync uses **two independent tables**: `votes_log` (Vote Sync) and `player_stats` (Leaderboard Sync).  
Each table can live in the **same** or **separate** Supabase projects.

---

## Table of Contents

1. [Create a Supabase Project](#1-create-a-supabase-project)
2. [Create the `votes_log` Table](#2-create-the-votes_log-table)
3. [Create the `player_stats` Table](#3-create-the-player_stats-table)
4. [Set Up Row Level Security (RLS) Policies](#4-set-up-row-level-security-rls-policies)
5. [Get Your API Keys](#5-get-your-api-keys)
6. [Configure `config.yml`](#6-configure-configyml)
7. [Verify Everything Works](#7-verify-everything-works)
8. [Web UI / API Usage Tips](#8-web-ui--api-usage-tips)

---

## 1. Create a Supabase Project

1. Go to [https://supabase.com](https://supabase.com) and sign in (or create an account).
2. Click **"New Project"**.
3. Choose your **Organization**, give it a name (e.g., `HavenCraft`), set a **database password**, and select a **region** close to your Minecraft server.
4. Wait for the project to finish provisioning (~2 minutes).

> You can use **one project** for both tables, or **two separate projects** if you want full isolation.

---

## 2. Create the `votes_log` Table

Go to your Supabase Dashboard → **SQL Editor** → click **"New query"** → paste the following and click **"Run"**:

```sql
-- ============================================================
-- VOTES LOG TABLE
-- ============================================================
-- Stores every vote received from Votifier.
-- Each row = one vote event from a player on a specific site.

CREATE TABLE IF NOT EXISTS votes_log (
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    player_uuid    UUID           NOT NULL,
    username       VARCHAR(32)    NOT NULL,
    site           VARCHAR(64)    NOT NULL,
    voted_at       TIMESTAMPTZ    DEFAULT NOW(),
    processed      BOOLEAN        DEFAULT FALSE
);

-- ─── Performance Indexes ───────────────────────────────────
-- Fast lookup by player (for profile pages)
CREATE INDEX idx_votes_player    ON votes_log (player_uuid);
-- Fast sorting by vote time (for recent votes feed)
CREATE INDEX idx_votes_voted_at  ON votes_log (voted_at DESC);
-- Fast monthly top voter queries
CREATE INDEX idx_votes_monthly   ON votes_log (username, voted_at);
```

### Column Reference

| Column        | Type         | Description                                      |
|---------------|--------------|--------------------------------------------------|
| `id`          | UUID         | Auto-generated unique ID for each vote row       |
| `player_uuid` | UUID         | Minecraft UUID of the voter                      |
| `username`    | VARCHAR(32)  | Minecraft username at the time of voting         |
| `site`        | VARCHAR(64)  | Normalized voting site name (e.g., `minecraftservers`) |
| `voted_at`    | TIMESTAMPTZ  | Timestamp when the vote was recorded (auto-set)  |
| `processed`   | BOOLEAN      | Reserved for future use (reward processing flag) |

---

## 3. Create the `player_stats` Table

In the **same SQL Editor**, run a **new query** with the following:

```sql
-- ============================================================
-- PLAYER STATS TABLE (Leaderboard)
-- ============================================================
-- Stores aggregated player statistics for the leaderboard.
-- The plugin upserts (INSERT or UPDATE) using the uuid as the
-- conflict resolution key, so each player always has exactly one row.

CREATE TABLE IF NOT EXISTS player_stats (
    uuid           UUID              PRIMARY KEY,
    username       VARCHAR(32)       NOT NULL,
    money          DOUBLE PRECISION  DEFAULT 0,
    playtime_sec   BIGINT            DEFAULT 0,
    kills          INT4              DEFAULT 0,
    deaths         INT4              DEFAULT 0,
    block_place    INT8              DEFAULT 0,
    block_break    INT8              DEFAULT 0,
    mobs_killed    INT4              DEFAULT 0,
    shards         DOUBLE PRECISION  DEFAULT 0,
    last_updated   TIMESTAMPTZ       DEFAULT NOW()
);

-- ─── Performance Indexes ───────────────────────────────────
-- Each index optimizes sorting for a specific leaderboard category.
CREATE INDEX idx_stats_money      ON player_stats (money DESC);
CREATE INDEX idx_stats_playtime   ON player_stats (playtime_sec DESC);
CREATE INDEX idx_stats_kills      ON player_stats (kills DESC);
CREATE INDEX idx_stats_deaths     ON player_stats (deaths DESC);
CREATE INDEX idx_stats_blocks_p   ON player_stats (block_place DESC);
CREATE INDEX idx_stats_blocks_b   ON player_stats (block_break DESC);
CREATE INDEX idx_stats_mobs       ON player_stats (mobs_killed DESC);
CREATE INDEX idx_stats_shards     ON player_stats (shards DESC);

-- ─── Auto-update last_updated on every upsert ──────────────
-- This trigger automatically sets last_updated to NOW() whenever
-- a row is inserted or updated, so the plugin doesn't need to send it.

CREATE OR REPLACE FUNCTION update_last_updated()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_player_stats_updated ON player_stats;
CREATE TRIGGER trg_player_stats_updated
    BEFORE INSERT OR UPDATE ON player_stats
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated();
```

> [!TIP]
> **Upgrading an Existing Setup?**  
> If you already created the `player_stats` table previously, you can simply run this migration query to add shards support:
> ```sql
> ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS shards DOUBLE PRECISION DEFAULT 0;
> CREATE INDEX IF NOT EXISTS idx_stats_shards ON player_stats (shards DESC);
> ```

### Column Reference

| Column         | Type              | Description                                    |
|----------------|-------------------|------------------------------------------------|
| `uuid`         | UUID              | Minecraft UUID — primary key (one row per player) |
| `username`     | VARCHAR(32)       | Latest known Minecraft username                |
| `money`        | DOUBLE PRECISION  | Vault economy balance                          |
| `playtime_sec` | BIGINT            | Total playtime in **seconds**                  |
| `kills`        | INT4              | Player kill count (PvP)                        |
| `deaths`       | INT4              | Total death count                              |
| `block_place`  | INT8              | Total blocks placed (from `minecraft:used`)    |
| `block_break`  | INT8              | Total blocks broken (from `minecraft:mined`)   |
| `mobs_killed`  | INT4              | Total mob kills                                |
| `shards`       | DOUBLE PRECISION  | HavenShard economy balance                     |
| `last_updated` | TIMESTAMPTZ       | Auto-updated timestamp on every upsert         |

---

## 4. Set Up Row Level Security (RLS) Policies

Supabase enables **Row Level Security (RLS)** by default on new tables. Because the server plugin uses the **`service_role` key** (which bypasses RLS completely), you only need to create policies to allow **public read access (SELECT)** for your website/front-end. 

> [!WARNING]
> Do NOT create INSERT, UPDATE, or ALL policies targeting the `public` or `anon` roles. Doing so would allow anyone on the internet to overwrite or delete your database records using the public `anon` key.

### Option A: Run Read-Only Policies via SQL Editor (Recommended)

Go to **SQL Editor** → **New query** → paste and **Run**:

```sql
-- Enable RLS on both tables
ALTER TABLE votes_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_stats ENABLE ROW LEVEL SECURITY;

-- Allow anonymous/public users to read votes (for website leaderboards/vote history)
DROP POLICY IF EXISTS "Allow public read votes_log" ON public.votes_log;
CREATE POLICY "Allow public read votes_log"
    ON public.votes_log
    FOR SELECT
    TO anon
    USING (true);

-- Allow anonymous/public users to read player stats (for website leaderboards)
DROP POLICY IF EXISTS "Allow public read player_stats" ON public.player_stats;
CREATE POLICY "Allow public read player_stats"
    ON public.player_stats
    FOR SELECT
    TO anon
    USING (true);
```

### Option B: Create Policies via Supabase UI

If you prefer the visual approach:

1. Go to **Authentication** → **Policies** in the sidebar.
2. Find the `player_stats` table → click **"New Policy"** (Create from scratch).
3. Set the following details:
   - **Name:** `Allow public read player_stats`
   - **Allowed operation:** `SELECT`
   - **Target roles:** `anon`
   - **USING expression:** `true`
4. Click **"Save Policy"**.
5. Repeat the exact same steps for the `votes_log` table (creating a `SELECT` policy named `Allow public read votes_log` targeting `anon` role with `USING: true`).

### Understanding the Policies

| Table | Policy Name | Target Role | Allowed Operation | Why |
|-------|-------------|-------------|-------------------|-----|
| `player_stats` | `Allow public read player_stats` | `anon` | SELECT | Website reads leaderboard data using public `anon` key |
| `votes_log` | `Allow public read votes_log` | `anon` | SELECT | Website reads vote history using public `anon` key |
| (Any) | (None needed for plugin) | — | — | Server plugin uses the `service_role` key, which bypasses RLS automatically to insert/update data. |

---

## 5. Get Your API Keys

1. In your Supabase Dashboard, go to **Settings** (gear icon) → **API**.
2. You will see:

| Key | Where to find | Use for |
|-----|---------------|---------|
| **Project URL** | `Settings → API → Project URL` | The base URL for REST API calls |
| **`anon` key** | `Settings → API → Project API keys → anon public` | Safe for frontend/website reads |
| **`service_role` key** | `Settings → API → Project API keys → service_role` | Full access — use in the Minecraft plugin |

> **Important:** Use the `service_role` key in `config.yml` for the Minecraft server plugin.
> It bypasses RLS completely, ensuring the plugin can always write data.
> Never expose this key in client-side code.

### Constructing the REST API URL

The plugin talks to Supabase via its REST API. The base URL format is:

```
https://<your-project-ref>.supabase.co/rest/v1
```

The **table name** is configured separately in `config.yml` (see Step 6).
The plugin automatically constructs the full URL: `base_url + "/" + table_name`.

**Example:**
```
Base URL: https://abcdef123456.supabase.co/rest/v1
Table:    votes_log
Result:   https://abcdef123456.supabase.co/rest/v1/votes_log
```

---

## 6. Configure `config.yml`

Open `plugins/HavenCore/HavenSync/config.yml` and fill in your values:

```yaml
vote-sync:
  enabled: true
  supabase_url: "https://YOUR_PROJECT_REF.supabase.co"
  api_key: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."  # service_role key
  connect_timeout_ms: 5000
  read_timeout_ms: 5000
  sync_interval_ticks: 600
  duplicate_window_seconds: 300
  debug: false
  table: "votes_log"
  columns:
    player_uuid: "player_uuid"
    username: "username"
    site: "site"

leaderboard-sync:
  enabled: true
  supabase_url: "https://YOUR_PROJECT_REF.supabase.co"
  api_key: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."  # service_role key
  connect_timeout_ms: 8000
  read_timeout_ms: 8000
  sync_interval_ticks: 1200
  max_batch_size: 50
  startup_historical_sync_enabled: true
  startup_historical_sync_delay_ticks: 200
  startup_historical_sync_batch_size: 300
  startup_historical_sync_batch_interval_ticks: 20
  debug: false
  table: "player_stats"
  upsert_conflict_key: "uuid"
  columns:
    uuid: "uuid"
    username: "username"
    money: "money"
    playtime_sec: "playtime_sec"
    kills: "kills"
    deaths: "deaths"
    block_break: "block_break"
    block_place: "block_place"
    mobs_killed: "mobs_killed"
```

> **Note:** Both sync modules can use the same or different Supabase projects.

### Custom Column Mapping

The `table` and `columns` settings let you use **any Supabase table schema** without modifying plugin code.

For example, if your Supabase table uses different column names:

| Your Supabase Column | Plugin Internal Name | Config Mapping |
|---------------------|-----------------------|----------------|
| `id`                | uuid                  | `uuid: "id"` |
| `player_name`       | username              | `username: "player_name"` |
| `balance`           | money                 | `money: "balance"` |
| `time_played`       | playtime_sec          | `playtime_sec: "time_played"` |

Your config would look like:

```yaml
leaderboard-sync:
  table: "my_custom_stats"
  upsert_conflict_key: "id"
  columns:
    uuid: "id"
    username: "player_name"
    money: "balance"
    playtime_sec: "time_played"
    kills: "kills"
    deaths: "deaths"
    block_break: "blocks_broken"
    block_place: "blocks_placed"
    mobs_killed: "mob_kills"
```

The `upsert_conflict_key` should match the column name used as the **primary/unique key** in your Supabase table (the value from `columns.uuid`, not the plugin internal name).

---

## 7. Verify Everything Works

After configuring and restarting the server:

1. **Check the `player_stats` table** in Supabase Dashboard → **Table Editor**.
   - You should see rows appearing for online players after the first sync interval (default: 60 seconds).
   
2. **Check the `votes_log` table** after a player votes.
   - Each vote should create a new row with the player UUID, username, and site name.

3. **Enable debug mode** temporarily if nothing appears:
   ```yaml
   debug: true
   ```
   This will log successful upserts to the console so you can confirm data is being sent.

4. **Common issues:**
   - `HTTP 401` → Wrong API key. Make sure you're using the `service_role` key.
   - `HTTP 404` → Wrong table name in the URL. Double-check spelling.
   - `HTTP 403` → RLS policies are blocking. Run the policies from Step 4.
   - `HTTP 409` → Conflict. Make sure `?on_conflict=uuid` is in the request (the plugin adds this automatically).

---

## 8. Web UI / API Usage Tips

If you're building a website that reads from these tables:

### Leaderboard Queries (using `anon` key)

```javascript
// Top 10 richest players
const { data } = await supabase
  .from('player_stats')
  .select('username, money')
  .order('money', { ascending: false })
  .limit(10);

// Top 10 by playtime (convert seconds to hours in frontend)
const { data } = await supabase
  .from('player_stats')
  .select('username, playtime_sec')
  .order('playtime_sec', { ascending: false })
  .limit(10);

// Search a specific player
const { data } = await supabase
  .from('player_stats')
  .select('*')
  .ilike('username', '%PlayerName%')
  .single();
```

### Vote Queries

```javascript
// Top voters this month
const startOfMonth = new Date();
startOfMonth.setDate(1);
startOfMonth.setHours(0, 0, 0, 0);

const { data } = await supabase
  .from('votes_log')
  .select('username, count:id.count()')
  .gte('voted_at', startOfMonth.toISOString())
  .order('count', { ascending: false })
  .limit(10);

// Recent votes feed
const { data } = await supabase
  .from('votes_log')
  .select('username, site, voted_at')
  .order('voted_at', { ascending: false })
  .limit(20);
```

### Useful SQL Views (Optional)

You can create views in the SQL Editor for common queries:

```sql
-- View: Top 10 players by each category (useful for API caching)
CREATE OR REPLACE VIEW top_players AS
SELECT
    username,
    money,
    playtime_sec,
    kills,
    deaths,
    block_place,
    block_break,
    mobs_killed,
    last_updated
FROM player_stats
ORDER BY money DESC
LIMIT 100;

-- View: Monthly top voters
CREATE OR REPLACE VIEW monthly_top_voters AS
SELECT
    username,
    COUNT(*) as vote_count
FROM votes_log
WHERE voted_at >= date_trunc('month', NOW())
GROUP BY username
ORDER BY vote_count DESC
LIMIT 10;
```

---

## Quick Reference: All SQL Commands Combined

For a fresh setup, you can run everything in one go:

```sql
-- ============================================================
-- COMPLETE HAVENSYNC DATABASE SETUP
-- Run this entire script in Supabase SQL Editor
-- ============================================================

-- 1. Votes Log Table
CREATE TABLE IF NOT EXISTS votes_log (
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    player_uuid    UUID           NOT NULL,
    username       VARCHAR(32)    NOT NULL,
    site           VARCHAR(64)    NOT NULL,
    voted_at       TIMESTAMPTZ    DEFAULT NOW(),
    processed      BOOLEAN        DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_votes_player    ON votes_log (player_uuid);
CREATE INDEX IF NOT EXISTS idx_votes_voted_at  ON votes_log (voted_at DESC);
CREATE INDEX IF NOT EXISTS idx_votes_monthly   ON votes_log (username, voted_at);

ALTER TABLE votes_log ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow public read votes_log" ON public.votes_log;
DROP POLICY IF EXISTS "Allow insert votes" ON public.votes_log;
DROP POLICY IF EXISTS "Allow public read votes" ON public.votes_log;

CREATE POLICY "Allow public read votes_log" ON public.votes_log
    FOR SELECT TO anon USING (true);


-- 2. Player Stats Table
CREATE TABLE IF NOT EXISTS player_stats (
    uuid           UUID              PRIMARY KEY,
    username       VARCHAR(32)       NOT NULL,
    money          DOUBLE PRECISION  DEFAULT 0,
    playtime_sec   BIGINT            DEFAULT 0,
    kills          INT4              DEFAULT 0,
    deaths         INT4              DEFAULT 0,
    block_place    INT8              DEFAULT 0,
    block_break    INT8              DEFAULT 0,
    mobs_killed    INT4              DEFAULT 0,
    last_updated   TIMESTAMPTZ       DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stats_money    ON player_stats (money DESC);
CREATE INDEX IF NOT EXISTS idx_stats_playtime ON player_stats (playtime_sec DESC);
CREATE INDEX IF NOT EXISTS idx_stats_kills    ON player_stats (kills DESC);
CREATE INDEX IF NOT EXISTS idx_stats_deaths   ON player_stats (deaths DESC);
CREATE INDEX IF NOT EXISTS idx_stats_blocks_p ON player_stats (block_place DESC);
CREATE INDEX IF NOT EXISTS idx_stats_blocks_b ON player_stats (block_break DESC);
CREATE INDEX IF NOT EXISTS idx_stats_mobs     ON player_stats (mobs_killed DESC);

CREATE OR REPLACE FUNCTION update_last_updated()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_player_stats_updated ON player_stats;
CREATE TRIGGER trg_player_stats_updated
    BEFORE INSERT OR UPDATE ON player_stats
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated();

ALTER TABLE player_stats ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow public read player_stats" ON public.player_stats;
DROP POLICY IF EXISTS "Allow upsert player stats" ON public.player_stats;
DROP POLICY IF EXISTS "Public Read Access" ON public.player_stats;

CREATE POLICY "Allow public read player_stats" ON public.player_stats
    FOR SELECT TO anon USING (true);
```
