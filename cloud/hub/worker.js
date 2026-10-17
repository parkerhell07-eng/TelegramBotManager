// ─────────────────────────────────────────────────────────────────────────────
// Telegram Bot Manager — Ready Cloud Hub ("کلود آماده") v1
//
// ONE Worker shared by every bot registered from the app. Deployed once by
// the app owner (see README.md next to this file); after that, app users get
// 24/7 cloud answering with a single tap — no Cloudflare account needed.
//
// Endpoints:
//   GET  /                      → health JSON { ok, service, ver }
//   POST /v1/register           {token, admin, cfg}  → {route, secret, url}
//   POST /v1/rules/{route}      {cfg}   header X-TBM-Secret
//   POST /v1/unregister/{route} {}      header X-TBM-Secret
//   POST /hook/{route}          Telegram updates (secret header checked)
//
// Storage: KV namespace "HUB".
//   bot:{route} → { tok(enc), secret, admin, cfg, bot, username, created,
//                   day, used, notifiedDay }
//   rl:{ip}:{day} → registration rate-limit counter (30/day/IP)
//
// Security:
//   - tokens validated against Telegram getMe at registration (possession of
//     the token proves control of the bot),
//   - bot tokens stored AES-256-GCM encrypted (key = SHA-256 of HUB_KEY
//     Worker secret; never plaintext in KV, never returned by any endpoint),
//   - every webhook call must carry the per-bot secret Telegram echoes in
//     X-Telegram-Bot-Api-Secret-Token,
//   - rules/unregister require the same per-bot secret.
//
// Fair use on the free tier: sampled per-bot daily counter (saves KV writes),
// soft cap 2000 handled messages/day/bot; over the cap the admin is notified
// once and further messages are ignored until the next day (UTC).
// ─────────────────────────────────────────────────────────────────────────────

const CAP = 2000;          // soft daily cap per bot
const REG_LIMIT = 30;      // registrations per IP per day

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === "GET") {
      if (path === "/" || path === "/healthz") {
        return json({ ok: true, service: "tbm-hub", ver: 1 });
      }
      return json({ ok: false, error: "not found" }, 404);
    }
    if (request.method !== "POST") {
      return json({ ok: false, error: "method not allowed" }, 405);
    }

    const parts = path.split("/").filter(Boolean);

    if (path === "/v1/register") return handleRegister(request, env, url);

    if (parts[0] === "v1" && parts[1] === "rules" && parts[2]) {
      return handleRules(request, env, parts[2]);
    }
    if (parts[0] === "v1" && parts[1] === "unregister" && parts[2]) {
      return handleUnregister(request, env, parts[2]);
    }
    if (parts[0] === "hook" && parts[1]) {
      return handleHook(request, env, parts[1]);
    }
    return json({ ok: false, error: "not found" }, 404);
  }
};

// ── helpers ──────────────────────────────────────────────────────────────────

function json(obj, status) {
  return new Response(JSON.stringify(obj), {
    status: status || 200,
    headers: { "Content-Type": "application/json; charset=utf-8" }
  });
}

function ok() {
  return new Response("ok");
}

function dayKey() {
  return new Date().toISOString().slice(0, 10).replace(/-/g, "");
}

function hex(nBytes) {
  const b = crypto.getRandomValues(new Uint8Array(nBytes));
  let out = "";
  for (let i = 0; i < b.length; i++) out += b[i].toString(16).padStart(2, "0");
  return out;
}

function b64enc(u8) {
  let s = "";
  for (let i = 0; i < u8.length; i++) s += String.fromCharCode(u8[i]);
  return btoa(s);
}

function b64dec(str) {
  const s = atob(str);
  const u8 = new Uint8Array(s.length);
  for (let i = 0; i < s.length; i++) u8[i] = s.charCodeAt(i);
  return u8;
}

async function encKey(env) {
  const raw = new TextEncoder().encode(env.HUB_KEY || "insecure-dev-key");
  const digest = await crypto.subtle.digest("SHA-256", raw);
  return crypto.subtle.importKey("raw", digest, "AES-GCM", false, ["encrypt", "decrypt"]);
}

async function enc(env, text) {
  const key = await encKey(env);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, new TextEncoder().encode(text));
  return b64enc(iv) + "." + b64enc(new Uint8Array(ct));
}

async function dec(env, blob) {
  const key = await encKey(env);
  const idx = blob.indexOf(".");
  const iv = b64dec(blob.slice(0, idx));
  const ct = b64dec(blob.slice(idx + 1));
  const pt = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, key, ct);
  return new TextDecoder().decode(pt);
}

async function tg(token, method, body) {
  try {
    const r = await fetch("https://api.telegram.org/bot" + token + "/" + method, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    return await r.json();
  } catch (e) {
    return null;
  }
}

async function getRec(env, route) {
  try {
    const raw = await env.HUB.get("bot:" + route);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

// ── registration ─────────────────────────────────────────────────────────────

async function handleRegister(request, env, url) {
  // light per-IP registration rate limit (2-day TTL keeps KV tidy)
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  const day = dayKey();
  const rlKey = "rl:" + ip + ":" + day;
  let regCount = 0;
  try {
    regCount = parseInt((await env.HUB.get(rlKey)) || "0", 10) || 0;
  } catch (e) {}
  if (regCount >= REG_LIMIT) {
    return json({ ok: false, error: "registration limit reached for today" }, 429);
  }

  let body;
  try {
    body = await request.json();
  } catch (e) {
    return json({ ok: false, error: "bad json" }, 400);
  }

  const token = String(body.token || "").trim();
  const admin = Number(body.admin || 0);
  const cfg = body.cfg;
  if (!/^\d+:[A-Za-z0-9_-]{30,}$/.test(token)) {
    return json({ ok: false, error: "invalid bot token format" }, 400);
  }
  if (!admin || admin <= 0) {
    return json({ ok: false, error: "missing admin chat id" }, 400);
  }
  if (!cfg || typeof cfg !== "object") {
    return json({ ok: false, error: "missing cfg" }, 400);
  }

  // Prove control of the bot before storing anything.
  const me = await tg(token, "getMe", {});
  if (!me || !me.ok || !me.result || !me.result.id) {
    return json({ ok: false, error: "bot token rejected by Telegram" }, 400);
  }

  const route = "b" + me.result.id; // deterministic: re-register = re-deploy
  const rec = {
    tok: await enc(env, token),
    secret: hex(24),
    admin: admin,
    cfg: cfg,
    bot: me.result.id,
    username: me.result.username || "",
    created: Date.now(),
    day: day,
    used: 0,
    notifiedDay: ""
  };
  try {
    await env.HUB.put("bot:" + route, JSON.stringify(rec));
    await env.HUB.put(rlKey, String(regCount + 1), { expirationTtl: 172800 });
  } catch (e) {
    return json({ ok: false, error: "storage unavailable" }, 503);
  }

  return json({
    ok: true,
    route: route,
    secret: rec.secret,
    url: "https://" + url.host + "/hook/" + route,
    bot: { id: me.result.id, username: rec.username, first_name: me.result.first_name || "" }
  });
}

// ── manage (rules / unregister) ──────────────────────────────────────────────

async function handleRules(request, env, route) {
  const rec = await getRec(env, route);
  if (!rec) return json({ ok: false, error: "unknown route" }, 404);
  if ((request.headers.get("X-TBM-Secret") || "") !== rec.secret) {
    return json({ ok: false, error: "forbidden" }, 403);
  }
  let body;
  try {
    body = await request.json();
  } catch (e) {
    return json({ ok: false, error: "bad json" }, 400);
  }
  if (!body.cfg || typeof body.cfg !== "object") {
    return json({ ok: false, error: "missing cfg" }, 400);
  }
  rec.cfg = body.cfg;
  if (Number(body.admin) > 0) rec.admin = Number(body.admin);
  rec.used = 0; // fresh rules, fresh day budget
  rec.day = dayKey();
  try {
    await env.HUB.put("bot:" + route, JSON.stringify(rec));
  } catch (e) {
    return json({ ok: false, error: "storage unavailable" }, 503);
  }
  return json({ ok: true });
}

async function handleUnregister(request, env, route) {
  const rec = await getRec(env, route);
  if (!rec) return json({ ok: true }); // already gone
  if ((request.headers.get("X-TBM-Secret") || "") !== rec.secret) {
    return json({ ok: false, error: "forbidden" }, 403);
  }
  try {
    await env.HUB.delete("bot:" + route);
  } catch (e) {
    return json({ ok: false, error: "storage unavailable" }, 503);
  }
  return json({ ok: true });
}

// ── telegram webhook ─────────────────────────────────────────────────────────

async function handleHook(request, env, route) {
  const rec = await getRec(env, route);
  if (!rec) return ok();
  if (request.headers.get("X-Telegram-Bot-Api-Secret-Token") !== rec.secret) {
    return new Response("forbidden", { status: 403 });
  }
  let update;
  try {
    update = await request.json();
  } catch (e) {
    return ok();
  }

  // Fair use: sampled daily counter. The reset is persisted on the first
  // message of each day, then only every 250th message — over the cap we
  // stop writing entirely except for the one-time admin notice (KV free
  // tier allows 1000 writes/day, shared by all bots).
  const today = dayKey();
  const dayChanged = rec.day !== today;
  if (dayChanged) {
    rec.day = today;
    rec.used = 0;
    rec.notifiedDay = "";
  }
  rec.used = (rec.used || 0) + 1;
  const overCap = rec.used > CAP;
  if (!overCap && (dayChanged || rec.used % 250 === 0)) {
    try {
      await env.HUB.put("bot:" + route, JSON.stringify(rec));
    } catch (e) {}
  }

  try {
    if (!overCap) {
      await process(update, env, rec);
    } else if (rec.notifiedDay !== today) {
      // notify the owner once per day, then persist the flag
      rec.notifiedDay = today;
      try {
        await env.HUB.put("bot:" + route, JSON.stringify(rec));
      } catch (e) {}
      const token = await dec(env, rec.tok);
      await tg(token, "sendMessage", {
        chat_id: rec.admin,
        text: "TBM Hub: daily limit (" + CAP + ") reached for this bot — messages are paused until tomorrow (UTC).\nکلود آماده: سقف روزانه این ربات پر شد؛ تا فردا (UTC) پیام‌ها پاسخ نمی‌گیرند."
      });
    }
  } catch (e) {
    // one bad update must never break the webhook contract
  }
  return ok();
}

// ── the reply engine (mirrors the per-bot worker + phone engine) ─────────────

function fill(tpl, msg, cfg) {
  const from = msg.from || {};
  const name = from.first_name || cfg.defaultName || "dear friend";
  const user = from.username ? "@" + from.username : "";
  return String(tpl)
    .split("{name}").join(name)
    .split("{username}").join(user)
    .split("{chat_id}").join(String(msg.chat.id))
    .split("{bot_name}").join(cfg.botName || "");
}

async function process(update, env, rec) {
  const msg = update.message;
  if (!msg || !msg.text) return; // media-only messages: documented limit
  const from = msg.from;
  if (from && from.is_bot) return;
  const chat = msg.chat;
  const text = msg.text.trim();
  const cfg = rec.cfg || {};
  const isGroup = chat.type === "group" || chat.type === "supergroup" || chat.type === "channel";
  const token = await dec(env, rec.tok);

  // Owner path: replying to a forwarded copy delivers to that user as the bot.
  if (from && rec.admin && from.id === rec.admin) {
    if (msg.reply_to_message) {
      const rt = msg.reply_to_message;
      let dest = 0;
      if (rt.forward_from && rt.forward_from.id) {
        dest = rt.forward_from.id;
      } else if (rt.text) {
        // our copies are plain texts ending with a "chat_id: N" line
        const m = String(rt.text).match(/chat_id:\s*(-?\d+)/);
        if (m) dest = parseInt(m[1], 10);
      }
      if (dest && dest !== rec.admin) {
        await tg(token, "sendMessage", { chat_id: dest, text: (cfg.relayPrefix || "") + text });
      } else {
        await tg(token, "sendMessage", { chat_id: rec.admin, text: cfg.hintReply || "Reply to a user's message to answer them." });
      }
    }
    return;
  }

  let reply = null;
  if (text.startsWith("/")) {
    const head = text.slice(1).split(" ")[0] || "";
    const target = (head.split("@")[0] || "").toLowerCase();
    const mention = head.indexOf("@") >= 0 ? head.split("@")[1].toLowerCase() : "";
    if (!isGroup || !mention || mention === (cfg.botUsername || "").toLowerCase()) {
      if (target === "start") {
        if (cfg.welcomeEnabled && cfg.welcomeText) reply = fill(cfg.welcomeText, msg, cfg);
      } else if (target === "help") {
        reply = fill(cfg.helpText, msg, cfg);
      } else if (target === "id") {
        reply = fill(cfg.idText, msg, cfg);
      } else if (target === "about") {
        reply = fill(cfg.aboutText, msg, cfg);
      } else {
        let hit = null;
        const cmds = cfg.commands || [];
        for (let i = 0; i < cmds.length; i++) {
          const c = (cmds[i].command || "").replace("/", "").toLowerCase();
          if (c === target && cmds[i].reply) {
            hit = cmds[i];
            break;
          }
        }
        if (hit) reply = fill(hit.reply, msg, cfg);
        else if (cfg.unknownText) reply = fill(cfg.unknownText, msg, cfg);
      }
    }
  } else if (!isGroup) {
    const lower = text.toLowerCase();
    const kws = cfg.keywords || [];
    for (let i = 0; i < kws.length; i++) {
      const key = (kws[i].keyword || "").trim().toLowerCase();
      if (key && lower.indexOf(key) >= 0 && kws[i].reply) {
        reply = fill(kws[i].reply, msg, cfg);
        break;
      }
    }
  }

  if (reply) {
    await tg(token, "sendMessage", { chat_id: chat.id, text: reply });
  }

  // Support inbox: copy every user message into the owner's private chat.
  if (cfg.forward !== false && rec.admin) {
    const who = ((from && from.first_name) || "") + (from && from.last_name ? " " + from.last_name : "");
    const uname = from && from.username ? " (@" + from.username + ")" : "";
    const label = ((who + uname).trim()) || chat.title || ("chat " + chat.id);
    const body = (cfg.forwardHeader || "New message for the bot:") + "\n" +
      (cfg.forwardFrom || "From") + ": " + label + "\nchat_id: " + chat.id + "\n\n" + text;
    await tg(token, "sendMessage", { chat_id: rec.admin, text: body, disable_notification: true });
  }
}
