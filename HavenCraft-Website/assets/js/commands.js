/* ============================================
   HavenCraft — Commands Wiki & Bilingual Toggle
   ============================================ */

(function () {
    'use strict';

    const TRANSLATIONS = {
        'header-subtitle': {
            en: 'Beginner-friendly guide to all features and commands',
            bn: 'নতুন প্লেয়ারদের জন্য সার্ভারের ফিচার ও কমান্ডের সহজ গাইডলাইন'
        },
        'intro': {
            en: 'Click on any section below to see all the commands and details for that specific feature.',
            bn: 'যেকোনো সেকশনে ক্লিক করে সেই ফিচারের ডিটেইলস এবং কমান্ডগুলো দেখে নিন।'
        },
        'sec-1-desc': {
            en: 'First time joining? Start here!',
            bn: 'প্রথমবার জয়েন করলে এখান থেকে শুরু করুন!'
        },
        'cmd-reg': {
            en: '<strong>First time playing:</strong> You must register your account to protect it. Pick a secret password and type it twice. Example: <code>/register MySecret123 MySecret123</code>',
            bn: '<strong>প্রথমবার জয়েন করলে:</strong> অ্যাকাউন্ট হ্যাক হওয়া থেকে বাঁচাতে রেজিস্টার করা বাধ্যতামূলক। একটি পাসওয়ার্ড পছন্দ করে দুইবার টাইপ করুন। যেমন: <code>/register MySecret123 MySecret123</code>'
        },
        'cmd-login': {
            en: '<strong>Returning players:</strong> Every time you join the server again, you need to log in with your password. Example: <code>/login MySecret123</code>',
            bn: '<strong>পরবর্তীতে আসলে:</strong> প্রতিবার সার্ভারে ঢোকার সময় আপনাকে পাসওয়ার্ড দিয়ে লগ-ইন করতে হবে। যেমন: <code>/login MySecret123</code>'
        },
        'sec-2-desc': {
            en: 'Teleporting, settings, AFK zone, and fun emotes',
            bn: 'টেলিপোর্ট, সেটিংস, AFK জোন এবং মজার ইমোটস'
        },
        'cmd-rtp': {
            en: 'Randomly teleport far away into the survival world so you can start building safely.',
            bn: 'Survival world-এ randomly teleport হয়ে দূরে কোথাও গিয়ে নিরাপদে বেস বানানোর জন্য এই কমান্ডটি ব্যবহার করুন।'
        },
        'cmd-spawn': {
            en: "Return to the server's main spawn area safely.",
            bn: 'সার্ভারের মেইন spawn এরিয়াতে নিরাপদে ফিরে যাওয়ার জন্য।'
        },
        'cmd-tpa': {
            en: 'Send a request to teleport to another player. They can type <code>/tpaccept</code> or <code>/tpdeny</code>.',
            bn: 'অন্য কোনো প্লেয়ারের কাছে teleport হওয়ার রিকোয়েস্ট পাঠান। তারা <code>/tpaccept</code> দিয়ে তা এক্সেপ্ট করতে পারবে।'
        },
        'cmd-sethome': {
            en: 'Save your current location. You can type <code>/home [name]</code> later to teleport back here instantly!',
            bn: 'বর্তমান লোকেশন সেভ করে রাখুন। পরবর্তীতে <code>/home [name]</code> লিখলেই সাথে সাথে এখানে teleport হয়ে চলে আসতে পারবেন!'
        },
        'cmd-afk': {
            en: 'Teleport to the AFK Zone. You can stand here while away from your keyboard and earn passive rewards!',
            bn: 'AFK Zone-এ teleport হোন। গেম চালু রেখে এখানে দাঁড়িয়ে থাকলে আপনি ফ্রিতে passive rewards পাবেন!'
        },
        'cmd-spawners': {
            en: 'Mine spawners using a Silk Touch pickaxe. If you place multiple spawners of the same type next to each other, they will automatically stack into one! Right click them to upgrade.',
            bn: 'Silk Touch pickaxe দিয়ে spawner ভাঙতে পারবেন। একই ধরনের spawner পাশাপাশি রাখলে সেগুলো একসাথে stack হয়ে যাবে! Right click করে এগুলো upgrade করা যায়।'
        },
        'cmd-settings': {
            en: 'Open the settings menu to toggle TPA requests, Global Chat, Private Messages, the Scoreboard, Night Vision, and more.',
            bn: 'এই GUI মেনু থেকে TPA রিকোয়েস্ট, Global Chat, Private Message, Scoreboard, Night Vision সহ আরও অনেক সেটিংস অন/অফ করতে পারবেন।'
        },
        'cmd-lb': {
            en: 'View the server leaderboard for Playtime, Kills, Deaths, etc.',
            bn: 'সার্ভারের Playtime, Kills, Deaths ইত্যাদির লিডারবোর্ড দেখুন।'
        },
        'cmd-stats': {
            en: "View your gameplay statistics (playtime, kills, deaths, placed/broken blocks). Add a player's name (e.g. <code>/stats player</code>) to check someone else.",
            bn: 'আপনার নিজের গেমপ্লে স্ট্যাটস (প্লেটাইম, কিলস, ডেথস, ব্লক ইত্যাদি) দেখুন। সাথে কোনো প্লেয়ারের নাম দিলে (যেমন: <code>/stats player</code>) তার প্রগ্রেস দেখতে পারবেন।'
        },
        'cmd-emotes': {
            en: 'Character emotes you can use anywhere in the world.',
            bn: 'গেমের যেকোনো জায়গায় বসে পড়া বা শোয়ার ইমোট কমান্ড।'
        },
        'sec-3-desc': {
            en: 'Protect your builds and items from griefers',
            bn: 'অন্যদের হাত থেকে নিজের জিনিসপত্র ও বেস সুরক্ষিত রাখুন'
        },
        'cmd-claim': {
            en: 'Use a <strong>Golden Shovel</strong> to claim land. Right-click one corner of your base, then right-click the opposite corner to block other players from editing inside.',
            bn: 'জায়গা ক্লেইম করতে <strong>Golden Shovel</strong> হাতে নিন। বেসের এক কোনায় রাইট-ক্লিক করে বিপরীত কোনায় আরেকবার রাইট-ক্লিক করলে অন্য কেউ ভেতরে ব্লক ভাঙতে বা বসাতে পারবে না।'
        },
        'cmd-trust': {
            en: 'Give your friend permission to build, open doors, and access chests inside your claimed land.',
            bn: 'আপনার ক্লেইম করা জায়গায় বন্ধুকে বিল্ড করা, দরজা খোলা এবং চেস্ট এক্সেস করার পারমিশন দিন।'
        },
        'cmd-untrust': {
            en: "Remove a player's access from your land immediately.",
            bn: 'আপনার জায়গা থেকে কোনো প্লেয়ারের এক্সেস বা পারমিশন সাথে সাথে রিমুভ করুন।'
        },
        'cmd-abandon': {
            en: 'Delete the claim you are standing in. <strong>Warning:</strong> Anyone will be able to break your blocks.',
            bn: 'আপনি যে ক্লেইমের ভেতর দাঁড়িয়ে আছেন সেটি ডিলিট করুন। <strong>সতর্কতা:</strong> এর পর যে কেউ আপনার ব্লক ভাঙতে পারবে।'
        },
        'cmd-claimslist': {
            en: 'See a list of all places you have claimed and their coordinates.',
            bn: 'আপনার ক্লেইম করা সব জায়গার লিস্ট ও কো-অর্ডিনেটস দেখুন।'
        },
        'sec-4-desc': {
            en: 'Earn money, buy supplies, and trade with other players',
            bn: 'টাকা আয় করুন, দরকারি জিনিস কিনুন ও প্লেয়ারদের সাথে ট্রেড করুন'
        },
        'cmd-shop': {
            en: 'Open the main server shop to buy blocks, food, gear, or sell farm drops.',
            bn: 'সার্ভারের মেইন shop খুলে Blocks, Food, Gear কিনুন বা ফার্মের জিনিস বিক্রি করুন।'
        },
        'cmd-sell': {
            en: 'Open a quick menu to drop survival items in and sell them immediately.',
            bn: 'একটি কুইক GUI ওপেন হবে। ভেতরে survival আইটেম রাখলেই সাথে সাথে বিক্রি হয়ে টাকা ব্যালেন্সে যোগ হবে।'
        },
        'cmd-price': {
            en: 'Open a category menu showing item prices so you know what each drop is worth.',
            bn: 'ক্যাটাগরি অনুযায়ী সব আইটেমের দাম দেখার মেন্যু ওপেন হবে।'
        },
        'cmd-bal': {
            en: 'Check how much money you currently have.',
            bn: 'আপনার কাছে বর্তমানে কত টাকা আছে তা দেখুন।'
        },
        'cmd-baltop': {
            en: 'View the richest players on the server.',
            bn: 'সার্ভারের সবচেয়ে ধনী প্লেয়ারদের তালিকা (baltop) দেখুন।'
        },
        'cmd-pay': {
            en: 'Transfer your money to another player.',
            bn: 'অন্য কোনো প্লেয়ারকে আপনার ব্যালেন্স থেকে টাকা পাঠান।'
        },
        'cmd-ah': {
            en: 'Open the Auction House to buy items listed by other players.',
            bn: 'Auction House ওপেন করে অন্য প্লেয়ারদের লিস্ট করা আইটেম কিনুন।'
        },
        'cmd-ahsell': {
            en: 'Hold an item in your hand and run this command to list it on the Auction House.',
            bn: 'যেকোনো আইটেম হাতে ধরে এই কমান্ডটি দিলে সেটি Auction House-এ বিক্রির জন্য লিস্ট হয়ে যাবে।'
        },
        'sec-5-desc': {
            en: 'Daily quests, rewards, physical items, and shard economy',
            bn: 'ডেইলি কোয়েস্ট, রিওয়ার্ডস, ফিজিক্যাল শার্ড আইটেম ও শার্ড ইকোনমি'
        },
        'cmd-shard': {
            en: 'Open the Shard Dashboard GUI to access your Profile, Daily Claims, Daily Quests, Rewards, Transaction History, and Top List.',
            bn: 'শার্ড ড্যাশবোর্ড GUI ওপেন করুন (প্রোফাইল, ডেইলি ক্লেইম, কোয়েস্ট, রিওয়ার্ডস, লেনদেন হিস্ট্রি ও টপ লিস্ট)।'
        },
        'cmd-shard-bal': {
            en: 'Check how many shards you currently have in your account balance.',
            bn: 'আপনার অ্যাকাউন্টে বর্তমানে কতগুলো শার্ড আছে তা চেক করুন।'
        },
        'cmd-shard-top': {
            en: 'View the top 10 shard holders on the server.',
            bn: 'সার্ভারের শীর্ষ ১০ জন শার্ড হোল্ডারের তালিকা দেখুন।'
        },
        'cmd-shard-history': {
            en: 'View your personal transaction log and recent shard activity.',
            bn: 'আপনার ব্যক্তিগত শার্ড লেনদেন ও হিস্ট্রি লগ দেখুন।'
        },
        'cmd-shard-pay': {
            en: 'Transfer shards from your account to another player.',
            bn: 'আপনার অ্যাকাউন্ট থেকে অন্য প্লেয়ারকে শার্ড পাঠান।'
        },
        'cmd-shard-withdraw': {
            en: 'Withdraw shards from your account balance as physical items in your inventory.',
            bn: 'আপনার অ্যাকাউন্ট ব্যালেন্স থেকে শার্ড উইথড্র করে ইনভেন্টরিতে ফিজিক্যাল আইটেম হিসেবে নিন।'
        },
        'cmd-shard-deposit': {
            en: 'Deposit physical shard items held in your hand back into your account balance.',
            bn: 'হাতে থাকা ফিজিক্যাল শার্ড আইটেম সরাসরি আপনার অ্যাকাউন্ট ব্যালেন্সে জমা করুন।'
        },
        'sec-6-desc': {
            en: 'Team up with friends, chat privately, and set allies',
            bn: 'বন্ধুদের সাথে টিম বানান, প্রাইভেট চ্যাট করুন ও মিত্র যোগ করুন'
        },
        'cmd-teamcreate': {
            en: 'Create your own team as its leader.',
            bn: 'নিজের একটি team বা ক্ল্যান তৈরি করুন এবং লিডার হোন।'
        },
        'cmd-teaminvite': {
            en: 'Invite a player to join your team.',
            bn: 'আপনার টিমে জয়েন করার জন্য কোনো প্লেয়ারকে ইনভাইট করুন।'
        },
        'cmd-teamjoin': {
            en: 'Accept an invitation and join a team.',
            bn: 'ইনভাইটেশন এক্সেপ্ট করে কোনো টিমে জয়েন করুন।'
        },
        'cmd-teamchat': {
            en: 'Toggle team chat so only your teammates can read your messages.',
            bn: 'Team chat অন/অফ করুন। এটি অন থাকলে শুধু আপনার টিমের সদস্যরাই মেসেজ দেখতে পাবে।'
        },
        'cmd-teamally': {
            en: 'Send an alliance request to another team. Allies cannot damage each other.',
            bn: 'অন্য কোনো টিমকে ally হওয়ার রিকোয়েস্ট পাঠান। Ally হলে কেউ কাউকে মারতে পারবে না।'
        }
    };

    function setLang(lang, persist = true) {
        if (persist) {
            try { localStorage.setItem('havencraft-lang', lang); } catch (_) {}
        }
        document.getElementById('btnEN')?.classList.toggle('active', lang === 'en');
        document.getElementById('btnBN')?.classList.toggle('active', lang === 'bn');
        const slider = document.getElementById('langSlider');
        if (slider) {
            slider.style.transform = lang === 'bn' ? 'translateX(100%)' : 'translateX(0)';
        }
        document.querySelectorAll('[data-translate]').forEach((el) => {
            const key = el.getAttribute('data-translate');
            if (TRANSLATIONS[key] && TRANSLATIONS[key][lang]) {
                el.innerHTML = TRANSLATIONS[key][lang];
            }
        });
    }

    function toggleWiki(header) {
        const section = header.parentElement;
        const isNowActive = section.classList.toggle('active');
        header.setAttribute('aria-expanded', isNowActive ? 'true' : 'false');
    }

    window.setLang = setLang;
    window.toggleWiki = toggleWiki;

    document.addEventListener('DOMContentLoaded', () => {
        try {
            if (localStorage.getItem('havencraft-lang') === 'bn') setLang('bn', false);
        } catch (_) {}

        const filterInput = document.getElementById('commandsSearchInput');
        const emptyStateEl = document.getElementById('commandsEmptyState');
        if (!filterInput) return;

        filterInput.addEventListener('input', () => {
            const q = filterInput.value.trim().toLowerCase();
            let visibleSections = 0;

            document.querySelectorAll('.wiki-section').forEach((section) => {
                const items = section.querySelectorAll('.cmd-item');
                let matchCount = 0;
                items.forEach((item) => {
                    const show = !q || item.textContent.toLowerCase().includes(q);
                    item.style.display = show ? '' : 'none';
                    if (show) matchCount++;
                });

                const sectionVisible = !(matchCount === 0 && q);
                section.style.display = sectionVisible ? '' : 'none';
                if (sectionVisible) visibleSections++;

                if (q && matchCount > 0) {
                    section.classList.add('active');
                    const hdr = section.querySelector('.wiki-header');
                    if (hdr) hdr.setAttribute('aria-expanded', 'true');
                }
            });

            if (emptyStateEl) {
                emptyStateEl.hidden = !(q && visibleSections === 0);
            }
        });
    });
})();
