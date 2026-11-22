/**
 * HavenCraft — Supabase Vote Engine
 * Handles user vote history, cooldown status, leaderboards, and wall of fame.
 */

const VOTE_CACHE_TTL = 120000; // 2 minutes

function getCachedVoteData(key) {
    try {
        const item = sessionStorage.getItem(key);
        if (!item) return null;
        const parsed = JSON.parse(item);
        if (Date.now() - parsed.timestamp < VOTE_CACHE_TTL) {
            return parsed.data;
        }
    } catch (_) {}
    return null;
}

function setCachedVoteData(key, data) {
    try {
        sessionStorage.setItem(key, JSON.stringify({ timestamp: Date.now(), data }));
    } catch (_) {}
}

function clearVoteCache() {
    try {
        [
            'hvn_vote_lb_daily',
            'hvn_vote_lb_weekly',
            'hvn_vote_lb_monthly',
            'hvn_vote_lb_alltime',
            'hvn_wof_alltime',
            'hvn_wof_monthly'
        ].forEach(k => sessionStorage.removeItem(k));
    } catch (_) {}
}

function escapeSql(str) {
    if (typeof window.escapeSqlWildcards === 'function') {
        return window.escapeSqlWildcards(str);
    }
    return String(str || '').replace(/[%_\\]/g, '\\$&');
}

function ensureSupabaseVoteClient() {
    if (window.supabaseVote) return window.supabaseVote;
    if (window.HavenCraftCore && typeof window.HavenCraftCore.getVote === 'function') {
        return window.HavenCraftCore.getVote();
    }
    return null;
}

async function fetchUserVotes(username) {
    const client = ensureSupabaseVoteClient();
    if (!client) {
        console.error('[Supabase] Vote client not initialized.');
        return { data: {}, error: 'Not initialized' };
    }

    const cleanUsername = (username || '').trim();
    if (!cleanUsername) {
        return { data: {}, error: 'Invalid username' };
    }

    const { data, error } = await client
        .from('votes')
        .select('site, created_at')
        .ilike('username', escapeSql(cleanUsername))
        .order('created_at', { ascending: false })
        .limit(200);

    if (error) {
        console.error('[Supabase] Error fetching votes:', error);
        return { data: {}, error };
    }

    const votesBySite = {};
    if (data) {
        for (const vote of data) {
            if (vote.site && vote.created_at && !votesBySite[vote.site]) {
                votesBySite[vote.site] = vote.created_at;
            }
        }
    }

    return { data: votesBySite, error: null };
}

async function fetchVoteRanking(period, limit, cachePrefix, label, forceRefresh = false) {
    const client = ensureSupabaseVoteClient();
    if (!client) {
        console.error('[Supabase] Vote client not initialized.');
        return [];
    }

    const cacheKey = `${cachePrefix}${period}`;
    if (!forceRefresh) {
        const cached = getCachedVoteData(cacheKey);
        if (cached) return cached;
    }

    const startDate = getPeriodStartISO(period);

    try {
        try {
            const { data: rpcData, error: rpcError } = await client
                .rpc('get_vote_leaderboard', { period_start: startDate, limit_count: limit });
            if (!rpcError && Array.isArray(rpcData) && rpcData.length > 0) {
                setCachedVoteData(cacheKey, rpcData);
                return rpcData;
            }
        } catch (_) {
            // Fall back to table scan if RPC is absent
        }

        const { data: rows, error } = await client
            .from('votes')
            .select('username')
            .gte('created_at', startDate)
            .order('created_at', { ascending: false })
            .limit(5000);

        if (error) {
            console.error(`[Supabase] ${label} fetch error:`, error.message, error.details || '');
            return [];
        }

        if (!rows || rows.length === 0) return [];

        const counts = {};
        for (const row of rows) {
            if (row.username) {
                counts[row.username] = (counts[row.username] || 0) + 1;
            }
        }

        const sorted = Object.entries(counts)
            .map(([username, vote_count]) => ({ username, vote_count }))
            .sort((a, b) => b.vote_count - a.vote_count)
            .slice(0, limit);

        setCachedVoteData(cacheKey, sorted);
        return sorted;
    } catch (err) {
        console.error(`[Supabase] ${label} fetch unexpected error:`, err);
        return [];
    }
}

function fetchLeaderboard(period = 'monthly', forceRefresh = false) {
    return fetchVoteRanking(period, 10, 'hvn_vote_lb_', 'Leaderboard', forceRefresh);
}

function fetchWallOfFame(period = 'alltime', forceRefresh = false) {
    return fetchVoteRanking(period, 3, 'hvn_wof_', 'Wall of Fame', forceRefresh);
}

async function fetchPlayerStats(username) {
    const client = ensureSupabaseVoteClient();
    if (!client) {
        console.error('[Supabase] Vote client not initialized.');
        return null;
    }

    const cleanUsername = (username || '').trim();
    if (!cleanUsername) return null;

    const { data: allVotes, error } = await client
        .from('votes')
        .select('created_at')
        .ilike('username', escapeSql(cleanUsername))
        .order('created_at', { ascending: false })
        .limit(5000);

    if (error || !allVotes || allVotes.length === 0) {
        return null;
    }

    const nowUtc = new Date();
    const bdTime = new Date(nowUtc.getTime() + 6 * 3600 * 1000);
    bdTime.setUTCHours(0, 0, 0, 0); // Midnight BDT

    const dayAgo = bdTime.getTime() - 6 * 3600 * 1000;
    const weekAgo = nowUtc.getTime() - 7 * 24 * 60 * 60 * 1000;
    const monthAgo = nowUtc.getTime() - 30 * 24 * 60 * 60 * 1000;

    let daily = 0, weekly = 0, monthly = 0;
    for (const vote of allVotes) {
        const t = new Date(vote.created_at).getTime();
        if (t >= dayAgo) daily++;
        if (t >= weekAgo) weekly++;
        if (t >= monthAgo) monthly++;
    }

    return {
        username: cleanUsername,
        total_votes: allVotes.length,
        daily_votes: daily,
        weekly_votes: weekly,
        monthly_votes: monthly,
        last_vote_at: allVotes[0].created_at
    };
}

function getPeriodStartISO(period) {
    const d = new Date();
    switch (period) {
        case 'daily': {
            const bd = new Date(d.getTime() + 6 * 3600 * 1000);
            bd.setUTCHours(0, 0, 0, 0);
            return new Date(bd.getTime() - 6 * 3600 * 1000).toISOString();
        }
        case 'weekly': d.setDate(d.getDate() - 7); break;
        case 'monthly': d.setDate(d.getDate() - 30); break;
        case 'alltime': return '2000-01-01T00:00:00Z';
        default: d.setDate(d.getDate() - 30);
    }
    return d.toISOString();
}

if (typeof window !== 'undefined') {
    window.fetchUserVotes = fetchUserVotes;
    window.fetchLeaderboard = fetchLeaderboard;
    window.fetchWallOfFame = fetchWallOfFame;
    window.fetchPlayerStats = fetchPlayerStats;
    window.clearVoteCache = clearVoteCache;
}
