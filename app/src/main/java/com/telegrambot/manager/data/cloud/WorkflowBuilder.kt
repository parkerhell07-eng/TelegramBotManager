package com.telegrambot.manager.data.cloud

import com.google.gson.Gson
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.BotAutoReply

/**
 * Builds the three files that make a bot answer 24/7 from GitHub Actions:
 *
 *   .github/workflows/engine.yml — cron every minute + manual dispatch;
 *         each run long-polls getUpdates (409 = another run alive → exit,
 *         which acts as free leader election) then amend-force-pushes the
 *         offset/checkin state so history stays one commit.
 *
 *   engine.py — stdlib-only Telegram poller/answerer; a faithful mirror of
 *         [com.telegrambot.manager.data.engine.BotEngine] (commands, keywords,
 *         welcome, unknown fallback, placeholders, group mention rules) plus
 *         owner inbox (copy of every user message to the admin chat) and
 *         admin relay (reply-to-copy → delivered to that user as the bot).
 *
 *   config.json — the bot's rules, regenerated on every "sync rules".
 */
object WorkflowBuilder {

    private val gson = Gson()

    // ── config.json ──────────────────────────────────────────────────────

    fun configJson(bot: Bot, cfg: BotAutoReply, adminId: Long, lang: String): String {
        val o = LinkedHashMap<String, Any>()
        o["bot_id"] = bot.telegramId
        o["bot_name"] = bot.name.ifBlank { "Bot" }
        o["bot_username"] = bot.username
        o["lang"] = if (lang == "en") "en" else "fa"
        o["admin_id"] = adminId
        o["welcome_enabled"] = cfg.welcomeEnabled
        o["welcome_text"] = cfg.welcomeText
        o["unknown_text"] = cfg.unknownText
        o["commands"] = cfg.commands.map { mapOf("command" to it.command, "reply" to it.reply) }
        o["keywords"] = cfg.keywords.map { mapOf("keyword" to it.keyword, "reply" to it.reply) }
        return gson.toJson(o)
    }

    // ── .github/workflows/engine.yml ─────────────────────────────────────

    fun engineYml(): String = """
        name: bot-engine
        on:
          schedule:
            - cron: '* * * * *'
          workflow_dispatch:
        permissions:
          contents: write
        jobs:
          engine:
            runs-on: ubuntu-latest
            timeout-minutes: 8
            steps:
              - uses: actions/checkout@v4
                with:
                  fetch-depth: 1
              - name: Run engine
                env:
                  BOT_TOKEN: @@GHA_SECRET@@
                run: python3 engine.py
              - name: Persist state
                if: always()
                run: |
                  git config user.email "tbm-engine@users.noreply.github.com"
                  git config user.name "tbm-engine"
                  git add -A state
                  git commit --amend -m "state" --no-edit || true
                  git push -f origin main || true
    """.trimIndent()
        .replace("@@GHA_SECRET@@", "${'$'}{{ secrets.BOT_TOKEN }}")

    // ── engine.py (stdlib only) ──────────────────────────────────────────

    fun enginePy(): String = """
import json, os, re, time, urllib.error, urllib.parse, urllib.request

API = "https://api.telegram.org"
TOKEN = os.environ.get("BOT_TOKEN", "").strip()

with open("config.json", encoding="utf-8") as f:
    CFG = json.load(f)

FA = CFG.get("lang", "fa") != "en"

TXT = {
    "help_head": "دستورات ربات {bot}:" if FA else "Commands of {bot}:",
    "l_start": "/start - " + ("شروع کار" if FA else "Get started"),
    "l_help": "/help - " + ("همین راهنما" if FA else "This help"),
    "l_id": "/id - " + ("شناسه چت شما" if FA else "Your chat id"),
    "l_about": "/about - " + ("درباره ربات" if FA else "About this bot"),
    "l_custom": "دستورهای اختصاصی:" if FA else "Custom commands:",
    "id_body": ("شناسه چت شما:\n{chat}\n\nاین عدد را در ابزارهای ارسال به‌عنوان chat_id وارد کنید."
                if FA else
                "Your chat id:\n{chat}\n\nUse this number as chat_id in the sending tools."),
    "about_body": "{bot}\n" + ("مدیریت‌شده با اپ «مدیر ربات تلگرام»." if FA
                               else "Managed by the Telegram Bot Manager app."),
    "default_name": "دوست عزیز" if FA else "dear friend",
    "inbox_head": "پیام جدید:" if FA else "New message:",
    "relay_hint": ("برای پاسخ به کاربر، روی پیامی که «chat_id» دارد ریپلای کنید."
                   if FA else
                   "To answer a user, reply to a message that carries a chat_id footer."),
    "footer": "chat_id",
    "state_dir": "state",
}

CHATS = []


def api(method, **params):
    data = urllib.parse.urlencode(params).encode("utf-8")
    req = urllib.request.Request(API + "/bot" + TOKEN + "/" + method, data=data)
    with urllib.request.urlopen(req, timeout=65) as r:
        return json.load(r)


def send(chat_id, text):
    return api("sendMessage", chat_id=chat_id, text=text)


def fill(tpl, chat, frm):
    name = (frm or {}).get("first_name") or ""
    uname = (frm or {}).get("username") or ""
    return (tpl
            .replace("{name}", name if name else TXT["default_name"])
            .replace("{username}", ("@" + uname) if uname else "")
            .replace("{chat_id}", str(chat.get("id", "")))
            .replace("{bot_name}", CFG.get("bot_name", "Bot")))


def build_help():
    lines = [TXT["help_head"].replace("{bot}", CFG.get("bot_name", "Bot")), TXT["l_start"],
             TXT["l_help"], TXT["l_id"], TXT["l_about"]]
    custom = [c for c in CFG.get("commands", []) if c.get("command", "").strip()]
    if custom:
        lines.append("")
        lines.append(TXT["l_custom"])
        for c in custom:
            lines.append("/" + c["command"].strip().lstrip("/"))
    return "\n".join(lines)


def decide(text, chat, frm):
    is_group = chat.get("type") in ("group", "supergroup", "channel")
    if text.startswith("/"):
        head = text[1:].split(" ")[0]
        target = head.split("@")[0].lower()
        if is_group:
            mention = head.split("@")[1].lower() if "@" in head else ""
            if mention and CFG.get("bot_username") and mention != CFG["bot_username"].lower():
                return None
        if target == "start":
            return fill(CFG.get("welcome_text", ""), chat, frm) if CFG.get("welcome_enabled") else None
        if target == "help":
            return fill(build_help(), chat, frm)
        if target == "id":
            return fill(TXT["id_body"].replace("{chat}", str(chat.get("id", ""))), chat, frm)
        if target == "about":
            return fill(TXT["about_body"], chat, frm)
        for c in CFG.get("commands", []):
            if c.get("command", "").strip().lstrip("/").lower() == target and c.get("reply", "").strip():
                return fill(c["reply"], chat, frm)
        unk = CFG.get("unknown_text", "")
        return fill(unk, chat, frm) if unk.strip() else None
    if is_group:
        return None
    lower = text.lower()
    for k in CFG.get("keywords", []):
        key = k.get("keyword", "").strip().lower()
        if key and key in lower and k.get("reply", "").strip():
            return fill(k["reply"], chat, frm)
    return None


def chat_label(chat):
    t = chat.get("title") or chat.get("first_name") or ""
    return t if t else str(chat.get("id", ""))


def track_chat(chat, frm):
    rec = {
        "id": chat.get("id"),
        "name": chat_label(chat),
        "username": (frm or {}).get("username") or chat.get("username") or "",
        "date": int(time.time()),
    }
    global CHATS
    CHATS = [c for c in CHATS if c.get("id") != rec["id"]]
    CHATS.insert(0, rec)
    del CHATS[30:]


def load_json(name, default):
    try:
        with open(os.path.join(TXT["state_dir"], name), encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default


def write_state(state, me, handled):
    os.makedirs(TXT["state_dir"], exist_ok=True)
    checkin = {
        "time": int(time.time()),
        "iso": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "offset": state.get("offset", 0),
        "handled_total": state.get("handled_total", 0),
        "runs": state.get("runs", 0),
        "me": {"id": me.get("id"), "username": me.get("username")},
        "last_chat": (CHATS[0] if CHATS else {}),
        "last_error": state.get("last_error", ""),
        "last_handled": handled,
    }
    with open(os.path.join(TXT["state_dir"], "state.json"), "w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False)
    with open(os.path.join(TXT["state_dir"], "checkin.json"), "w", encoding="utf-8") as f:
        json.dump(checkin, f, ensure_ascii=False)
    with open(os.path.join(TXT["state_dir"], "chats.json"), "w", encoding="utf-8") as f:
        json.dump({"chats": CHATS}, f, ensure_ascii=False)


def main():
    global CHATS
    CHATS = load_json("chats.json", {}).get("chats", [])
    state = load_json("state.json", {"offset": 0, "handled_total": 0, "runs": 0})
    offset = state.get("offset", 0)
    drained = offset > 0
    handled = 0
    err = ""
    me = {}
    try:
        me = api("getMe").get("result", {})
    except Exception:
        pass

    def upd(u):
        nonlocal offset, handled
        m = u.get("message") or u.get("edited_message")
        if not m:
            return
        chat = m.get("chat") or {}
        frm = m.get("from") or {}
        if frm.get("is_bot"):
            return
        track_chat(chat, frm)
        cid = chat.get("id")
        admin = int(CFG.get("admin_id") or 0)
        text = (m.get("text") or m.get("caption") or "").strip()

        if admin and cid == admin:
            # Admin relay: reply-to-copy delivers to that user as the bot.
            reply = m.get("reply_to_message") or {}
            src = reply.get("text") or reply.get("caption") or ""
            match = re.search(TXT["footer"] + r":\s*(-?\d+)", src)
            if match and text and not text.startswith("/"):
                try:
                    send(match.group(1), text)
                    handled += 1
                except Exception:
                    pass
                return
            if not text.startswith("/") and not match:
                try:
                    send(admin, TXT["relay_hint"])
                except Exception:
                    pass
                return

        # Owner inbox: copy every user message to the admin chat.
        if admin and cid != admin:
            try:
                head = TXT["inbox_head"] + " " + chat_label(chat)
                footer = "\n\n" + TXT["footer"] + ": " + str(cid)
                has_media = any(k in m for k in
                                ("photo", "video", "document", "audio", "voice", "video_note"))
                if "text" in m and text:
                    send(admin, head + "\n\n" + text + footer)
                elif has_media:
                    cap = (m.get("caption") or "")
                    api("copyMessage", chat_id=admin, from_chat_id=cid,
                        message_id=m.get("message_id"), caption=(cap + footer)[:1000])
                else:
                    api("copyMessage", chat_id=admin, from_chat_id=cid,
                        message_id=m.get("message_id"))
                    send(admin, footer.strip())
            except Exception:
                pass

        if text:
            try:
                reply = decide(text, chat, frm)
                if reply:
                    send(cid, reply)
                    handled += 1
                    time.sleep(0.08)
            except Exception:
                pass

    start = time.time()
    rounds = 0
    while rounds < 4 and time.time() - start < 150:
        rounds += 1
        try:
            params = {"timeout": 40, "allowed_updates": json.dumps(["message"])}
            if offset > 0:
                params["offset"] = offset
            r = api("getUpdates", **params)
            items = r.get("result", [])
        except urllib.error.HTTPError as e:
            if e.code == 409:
                # Another engine run holds the poll — free leader election.
                print("409: another run is live; exiting")
                state["runs"] = state.get("runs", 0) + 1
                state["last_error"] = ""
                write_state(state, me, 0)
                return
            err = "getUpdates HTTP " + str(e.code)
            time.sleep(3)
            continue
        except Exception as e:
            err = str(e)[:200]
            time.sleep(3)
            continue
        if not items:
            continue
        if not drained:
            # First ever run: swallow the backlog silently (no replies).
            offset = max(u["update_id"] for u in items) + 1
            drained = True
            continue
        for u in items:
            if u["update_id"] >= offset:
                offset = u["update_id"] + 1
            try:
                upd(u)
            except Exception:
                pass
        state["offset"] = offset

    try:
        api("getUpdates", offset=offset, timeout=0)  # confirm consumption
    except Exception:
        pass
    state["runs"] = state.get("runs", 0) + 1
    state["handled_total"] = state.get("handled_total", 0) + handled
    state["last_error"] = err
    write_state(state, me, handled)


if __name__ == "__main__":
    main()
""".trim()

    // ── checkin.json parsing (app side) ──────────────────────────────────

    data class Checkin(
        val time: Long = 0,
        val iso: String = "",
        val offset: Long = 0,
        val handled_total: Long = 0,
        val runs: Long = 0,
        val me: Me? = null,
        val last_chat: LastChat? = null,
        val last_error: String = "",
        val last_handled: Int = 0
    ) {
        data class Me(val id: Long = 0, val username: String? = null)
        data class LastChat(
            val id: Long = 0,
            val name: String = "",
            val username: String? = null,
            val date: Long = 0
        )
    }

    data class ChatsFile(val chats: List<Checkin.LastChat> = emptyList())

    fun parseCheckin(bytes: ByteArray?): Checkin? = runCatching {
        gson.fromJson(String(bytes ?: return null, Charsets.UTF_8), Checkin::class.java)
    }.getOrNull()

    fun parseChats(bytes: ByteArray?): ChatsFile = runCatching {
        gson.fromJson(String(bytes ?: return ChatsFile(), Charsets.UTF_8), ChatsFile::class.java)
    }.getOrDefault(ChatsFile())
}
