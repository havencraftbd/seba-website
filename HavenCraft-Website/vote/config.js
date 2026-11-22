/**
 * HavenCraft — Voting Sites Configuration (Single Source of Truth)
 *
 * The 'id' MUST match the canonical site name mapped in cooldown.js.
 */
const VOTE_SITES = Object.freeze([
    { id: 'havencraft', name: 'TopMinecraftServers.org', desc: 'Top Minecraft server list', icon: 'fa-globe', url: 'https://topminecraftservers.org/vote/43251', cooldown_hours: 6 },
    { id: 'minecraft-mp', name: 'Minecraft-MP.com', desc: 'Minecraft server directory', icon: 'fa-server', url: 'https://minecraft-mp.com/server/356927/vote/', cooldown_hours: 24 },
    { id: 'planetminecraft', name: 'PlanetMinecraft.com', desc: 'Community server voting', icon: 'fa-cube', url: 'https://www.planetminecraft.com/server/havencraft-smp/vote/', cooldown_hours: 24 },
    { id: 'mclist', name: 'Mclist.io', desc: 'Server ranking platform', icon: 'fa-list', url: 'https://mclist.io/server/67828-mc-havencraft-pro-havencraft-your-1-21-survi/vote', cooldown_hours: 24 },
    { id: 'minecraftservers', name: 'MinecraftServers.org', desc: 'Server listing site', icon: 'fa-network-wired', url: 'https://minecraftservers.org/vote/686398', cooldown_hours: 24 },
    { id: 'topg', name: 'TopG.org', desc: 'Gaming server votes', icon: 'fa-star', url: 'https://topg.org/minecraft-servers/server-681576', cooldown_hours: 6 },
    { id: 'minecraftkrant', name: 'MineCraftkrant.nl', desc: 'Server listing page', icon: 'fa-globe', url: 'https://minecraftkrant.nl/server/havencraft/vote', cooldown_hours: 24 },
    { id: 'minecraftiplist', name: 'minecraftiplist.com', desc: 'Server listing page', icon: 'fa-cube', url: 'https://www.minecraftiplist.com/server/HavenCraft-41830', cooldown_hours: 24 }
]);

function getVoteUrl(site) {
    return (site && site.url) ? site.url : '#';
}

window.VOTE_SITES = VOTE_SITES;
window.getVoteUrl = getVoteUrl;
