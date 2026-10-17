package com.telegrambot.manager.data.assistant

/**
 * Rule-based smart assistant engine: turns a short description of the
 * user's business into a complete, ready-to-apply bot package
 * (identity, command menu, welcome message, message templates, poll idea
 * and a weekly campaign idea). Fully offline — no external AI service.
 */
object AssistantEngine {

    enum class Type(val label: String) {
        SHOP("فروشگاه / فروش آنلاین"),
        RESTAURANT("رستوران / کافه"),
        CLINIC("کلینیک / سلامت"),
        EDUCATION("آموزشگاه / مدرس"),
        GYM("باشگاه / ورزش"),
        SERVICES("خدمات (فنی، زیبایی، مشاوره…)"),
        CONTENT("رسانه / کانال محتوایی"),
        OTHER("سایر کسب‌وکارها")
    }

    enum class Goal(val label: String) {
        SALES("فروش بیشتر"),
        SUPPORT("پشتیبانی مشتری"),
        BOOKING("رزرو / نوبت‌دهی"),
        NEWS("اطلاع‌رسانی"),
        LEARNING("آموزش و محتوا")
    }

    /** The generated, ready-to-apply package. */
    data class Package(
        val botName: String,
        val botDescription: String,
        val botShortDescription: String,
        val commands: List<Pair<String, String>>, // command (no slash) → description
        val welcome: String,                      // reply for /start (with variables)
        val templates: List<Pair<String, String>>, // title → body (with variables)
        val pollQuestion: String,
        val pollOptions: List<String>,
        val weeklyIdea: String,
        val tips: List<String>
    )

    fun build(
        businessName: String,
        type: Type,
        goals: Set<Goal>,
        notes: String
    ): Package {
        val n = businessName.ifBlank { "کسب‌وکار شما" }
        val extra = notes.trim()

        val (name, desc, short) = identity(n, type, extra)
        val commands = commandsFor(type, goals)
        val welcome = welcomeFor(n, type, goals, extra)
        val templates = templatesFor(n, type, goals, extra)
        val poll = pollFor(n, type, goals)
        val weekly = weeklyFor(n, type, goals)

        val tips = mutableListOf(
            "پیام خوش‌آمد را در قالب‌ها ذخیره کنید و هر وقت عضو جدید آمد برایش بفرستید.",
            "منوی دستورات را اعمال کنید تا مشتریان با / شروع کنند و همه‌چیز را سریع پیدا کنند."
        )
        if (goals.contains(Goal.SALES)) {
            tips.add("با ابزار «زمان‌بندی پیام»، پیام تخفیف هفتگی را برای پنجشنبه‌ها تنظیم کنید؛ بهترین ساعت فروش ۱۸ تا ۲۱ است.")
        }
        if (goals.contains(Goal.BOOKING)) {
            tips.add("برای رزروها یک چت اختصاصی بسازید: مشتری با /booking پیام می‌دهد و شما از بخش «چت‌ها» جواب می‌دهید.")
        }
        if (goals.contains(Goal.NEWS)) {
            tips.add("هر هفته یک نظرسنجی کوتاه بگذارید تا سلیقهٔ مشتریان را بشناسید؛ ابزار «نظرسنجی» آماده است.")
        }
        tips.add("در «آمار ربات» ببینید کدام مخاطب بیشترین تعامل را دارد و برایش پیشنهاد ویژه بفرستید.")

        return Package(name, desc, short, commands, welcome, templates, poll.first, poll.second, weekly, tips)
    }

    // ── Identity ─────────────────────────────────────────────────────────

    private fun identity(
        n: String,
        type: Type,
        extra: String
    ): Triple<String, String, String> {
        val name = when (type) {
            Type.SHOP -> "🛍 ربات فروشگاه $n"
            Type.RESTAURANT -> "☕️ ربات $n"
            Type.CLINIC -> "🩺 ربات $n"
            Type.EDUCATION -> "🎓 ربات آموزشی $n"
            Type.GYM -> "💪 ربات $n"
            Type.SERVICES -> "🛠 ربات $n"
            Type.CONTENT -> "📣 ربات $n"
            Type.OTHER -> "🤖 ربات $n"
        }
        val desc = when (type) {
            Type.SHOP ->
                "ربات رسمی فروشگاه $n — دریافت لیست قیمت، ثبت سفارش، اطلاع از تخفیف‌ها و پیگیری سفارش. ${extra.take(140)}".trim()
            Type.RESTAURANT ->
                "ربات رسمی $n — مشاهده منو، ثبت رزرو میز، تخفیف‌های روز و سفارش تلفن‌شده. ${extra.take(140)}".trim()
            Type.CLINIC ->
                "ربات رسمی $n — نوبت‌دهی آنلاین، ساعات کاری پزشکان، خدمات و راهنمای مراجعه. ${extra.take(140)}".trim()
            Type.EDUCATION ->
                "ربات آموزشی $n — اطلاع از دوره‌ها، ثبت‌نام، کلاس‌ها و پشتیبانی دانشجویان. ${extra.take(140)}".trim()
            Type.GYM ->
                "ربات رسمی $n — ثبت‌نام کلاس‌ها، ساعات شلوغی، برنامه‌های تمرینی و عضویت. ${extra.take(140)}".trim()
            Type.SERVICES ->
                "ربات رسمی $n — درخواست خدمات، تعیین وقت، لیست خدمات و قیمت‌ها. ${extra.take(140)}".trim()
            Type.CONTENT ->
                "ربات رسمی $n — دریافت جدیدترین محتواها، پیشنهادها و ارتباط با ما. ${extra.take(140)}".trim()
            Type.OTHER ->
                "ربات رسمی $n — پاسخ سریع به سوالات، خدمات و راه‌های ارتباطی. ${extra.take(140)}".trim()
        }
        val short = when (type) {
            Type.SHOP -> "لیست قیمت، سفارش و تخفیف‌های $n"
            Type.RESTAURANT -> "منو، رزرو میز و تخفیف‌های روز $n"
            Type.CLINIC -> "نوبت‌دهی و اطلاعات $n"
            Type.EDUCATION -> "دوره‌ها و ثبت‌نام $n"
            Type.GYM -> "کلاس‌ها و عضویت $n"
            Type.SERVICES -> "درخواست خدمات و وقت $n"
            Type.CONTENT -> "جدیدترین محتواهای $n"
            Type.OTHER -> "پشتیبانی و خدمات $n"
        }
        return Triple(name.take(64), desc.take(255), short.take(120))
    }

    // ── Commands ─────────────────────────────────────────────────────────

    private fun commandsFor(type: Type, goals: Set<Goal>): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>(
            "start" to "شروع کار با ربات و پیام خوش‌آمد",
            "menu" to when (type) {
                Type.RESTAURANT -> "مشاهده منو و قیمت‌ها"
                Type.SHOP -> "مشاهده دسته‌بندی محصولات"
                else -> "مشاهده خدمات و امکانات"
            },
            "hours" to "ساعات کاری و آدرس",
            "contact" to "راه‌های ارتباط با ما"
        )
        when (type) {
            Type.SHOP -> {
                list += "price" to "لیست قیمت محصولات"
                list += "order" to "راهنمای ثبت سفارش"
            }
            Type.RESTAURANT -> list += "reserve" to "رزرو میز"
            Type.CLINIC, Type.SERVICES -> list += "booking" to "تعیین وقت و نوبت‌دهی"
            Type.EDUCATION -> {
                list += "courses" to "لیست دوره‌ها و زمان‌بندی"
                list += "register" to "راهنمای ثبت‌نام"
            }
            Type.GYM -> {
                list += "classes" to "جدول کلاس‌ها"
                list += "membership" to "اشتراک و عضویت"
            }
            Type.CONTENT -> list += "latest" to "جدیدترین محتواها"
            Type.OTHER -> Unit
        }
        if (goals.contains(Goal.SALES)) list += "offers" to "تخفیف‌ها و پیشنهادهای ویژه"
        if (goals.contains(Goal.SUPPORT)) list += "support" to "پشتیبانی و پاسخ به سوالات"
        if (goals.contains(Goal.NEWS)) list += "news" to "آخرین اخبار و اطلاعیه‌ها"
        if (goals.contains(Goal.LEARNING) && type != Type.EDUCATION) {
            list += "learn" to "محتوای آموزشی رایگان"
        }
        if (list.none { it.first == "support" }) list += "support" to "پشتیبانی"
        // Telegram allows at most 100 commands; keep the menu focused.
        return list.take(12)
    }

    // ── Welcome ──────────────────────────────────────────────────────────

    private fun welcomeFor(
        n: String,
        type: Type,
        goals: Set<Goal>,
        extra: String
    ): String {
        val head = when (type) {
            Type.SHOP -> "سلام {first_name} عزیز 👋\nبه فروشگاه $n خوش آمدید!"
            Type.RESTAURANT -> "سلام {first_name} جان 👋\nبه $n خوش آمدید!"
            Type.CLINIC -> "سلام {first_name} عزیز 🌿\nبه $n خوش آمدید."
            Type.EDUCATION -> "سلام {first_name} عزیز 🎓\nبه $n خوش آمدید!"
            Type.GYM -> "سلام {first_name} 💪\nبه $n خوش آمدید!"
            Type.SERVICES -> "سلام {first_name} عزیز 👋\nبه $n خوش آمدید."
            Type.CONTENT -> "سلام {first_name} 👋\nبه کانال و ربات $n خوش آمدید!"
            Type.OTHER -> "سلام {first_name} عزیز 👋\nبه $n خوش آمدید."
        }
        val mid = when (type) {
            Type.SHOP -> "\n\nبرای دیدن محصولات /menu و لیست قیمت /price را بزنید. تخفیف‌های هفتگی هم در /offers منتشر می‌شود."
            Type.RESTAURANT -> "\n\nمنوی کامل را با /menu ببینید و میز خود را با /reserve رزرو کنید."
            Type.CLINIC -> "\n\nبرای نوبت‌گیری /booking و مشاهده ساعات کاری /hours را بزنید."
            Type.EDUCATION -> "\n\nلیست دوره‌ها: /courses — راهنمای ثبت‌نام: /register"
            Type.GYM -> "\n\nجدول کلاس‌ها: /classes — اطلاعات عضویت: /membership"
            Type.SERVICES -> "\n\nبرای درخواست خدمات /booking و مشاهده خدمات /menu را بزنید."
            Type.CONTENT -> "\n\nجدیدترین محتواها: /latest — پیشنهادهای ویژه: /offers"
            Type.OTHER -> "\n\nبرای شروع از منوی پایین ربات استفاده کنید."
        }
        val tail = if (extra.isNotBlank()) {
            "\n\nاگر سوال خاصی دارید، همین‌جا پیام بگذارید؛ سریع پاسخ می‌دهیم. ✨"
        } else {
            "\n\nهر سوالی داشتید همین‌جا پیام بدهید؛ سریع جواب می‌گیرید. ✨"
        }
        return head + mid + tail
    }

    // ── Templates ────────────────────────────────────────────────────────

    private fun templatesFor(
        n: String,
        type: Type,
        goals: Set<Goal>,
        extra: String
    ): List<Pair<String, String>> {
        val list = mutableListOf(
            "خوش‌آمدگویی" to welcomeFor(n, type, goals, extra),
            "پیشنهاد ویژه" to when (type) {
                Type.SHOP -> "🔥 {first_name} جان، فقط تا آخر هفته!\nتخفیف ویژهٔ محصولات منتخب $n — با کد «سلام۱۴۰۴».\nبرای مشاهده /price را بزنید."
                Type.RESTAURANT -> "🍽 {first_name} جان، پیشنهاد ویژهٔ امروز $n:\nیک غذا بگیر، دومی نصف قیمت!\nرزرو: /reserve"
                Type.CLINIC -> "🌿 {first_name} عزیز، این هفته در $n:\nمعاینهٔ رایگان برای اعضای ربات.\nنوبت بگیر: /booking"
                Type.EDUCATION -> "🎓 {first_name} جان، ظرفیت دوره‌های $n رو به اتمام است!\nثبت‌نام: /register"
                Type.GYM -> "💪 {first_name}، ثبت‌نام کلاس‌های جدید $n شروع شد!\nاولین جلسه رایگان است. جدول کلاس‌ها: /classes"
                Type.SERVICES -> "✨ {first_name} جان، تا آخر این هفته ۲۰٪ تخفیف اولین سفارش در $n!\nدرخواست: /booking"
                Type.CONTENT -> "📣 {first_name} جان، جدیدترین محتوای $n منتشر شد!\nمشاهده: /latest"
                Type.OTHER -> "🎁 {first_name} جان، پیشنهاد ویژهٔ این هفتهٔ $n را از دست نده!\nاطلاعات بیشتر: /menu"
            },
            "یادآوری / پیگیری" to when (type) {
                Type.SHOP -> "سلام {first_name}، سفارش شما در $n ثبت شد ✅ به‌محض ارسال، کد رهگیری را همین‌جا برایتان می‌فرستیم."
                Type.RESTAURANT -> "سلام {first_name}، رزرو شما در $n تأیید شد ✅ منتظرتان هستیم!\nبرای تغییر زمان، همین پیام را جواب بدهید."
                Type.CLINIC -> "سلام {first_name} عزیز، یادآوری نوبت شما در $n 🗓\nلطفاً ۱۵ دقیقه قبل از وقت خود حاضر باشید."
                Type.EDUCATION -> "سلام {first_name}، یادآوری: کلاس بعدی $n فردا برگزار می‌شود 📚\nجزوهٔ این جلسه پیوست شده است."
                Type.GYM -> "سلام {first_name} 💪 تمرین امروزت را فراموش نکن!\nجدول کلاس‌ها: /classes"
                Type.SERVICES -> "سلام {first_name}، وقت شما در $n ثبت شد ✅ در صورت تغییر، اطلاع دهید."
                Type.CONTENT -> "سلام {first_name}، محتوای جدید $n آماده است 📣 همین حالا ببینید!"
                Type.OTHER -> "سلام {first_name}، درخواست شما در $n در حال پیگیری است ✅"
            }
        )
        if (goals.contains(Goal.SUPPORT)) {
            list += "پاسخ به تیکت" to "سلام {first_name}، درخواست پشتیبانی شما در $n بررسی شد.\nراهنمایی: لطفاً شماره سفارش/وقت خود را هم ارسال کنید تا سریع‌تر رسیدگی شود. 🙏"
        }
        if (goals.contains(Goal.NEWS)) {
            list += "اطلاعیه خبری" to "📢 خبر مهم از $n:\n{first_name} جان، ساعات کاری ما در این هفته تغییر کرده است.\nساعات جدید: /hours"
        }
        if (goals.contains(Goal.LEARNING)) {
            list += "محتوای آموزشی" to "📚 نکتهٔ امروز $n:\n{first_name} جان، یک تکنیک ساده که همین امروز می‌توانی استفاده کنی:\n۱. هدف هفته را مشخص کن.\n۲. هر روز ۲۰ دقیقه تمرین.\n۳. نتیجه را یادداشت کن.\nبقیه نکته‌ها: /learn"
        }
        return list
    }

    // ── Poll ─────────────────────────────────────────────────────────────

    private fun pollFor(
        n: String,
        type: Type,
        goals: Set<Goal>
    ): Pair<String, List<String>> = when {
        goals.contains(Goal.SALES) -> "دوست دارید $n چه تخفیفی برای شما بگذارد؟" to
            listOf("تخفیف درصدی روی همه‌چیز", "خرید یک بگیر دو بگیر", "ارسال رایگان", "کارت هدیه")
        type == Type.RESTAURANT -> "کدام غذای $n را بیشتر دوست دارید؟" to
            listOf("غذاهای کلاسیک منو", "غذاهای جدید", "پیش‌غذا و دسر", "نوشیدنی‌ها")
        type == Type.CLINIC -> "کدام خدمت $n برای شما مفیدتر است؟" to
            listOf("نوبت‌دهی آنلاین", "پاسخ به سوالات پزشکی", "مقالات آموزشی", "یادآوری ویزیت")
        type == Type.EDUCATION -> "چه محتوایی از $n برایتان ارزشمندتر است؟" to
            listOf("کلاس زنده", "فایل‌های آموزشی", "تمرین و آزمون", "مشاوره تحصیلی")
        type == Type.GYM -> "بهترین ساعت تمرین شما در $n کیست؟" to
            listOf("صبح زود", "ظهر", "عصر", "شب")
        goals.contains(Goal.SUPPORT) -> "کیفیت پاسخ‌گویی $n را چطور ارزیابی می‌کنید؟" to
            listOf("عالی", "خوب", "متوسط", "نیاز به بهبود")
        else -> "چه محتوایی دوست دارید از $n ببینید؟" to
            listOf("معرفی محصولات و خدمات", "نکات آموزشی", "تخفیف‌ها و اخبار", "پشت‌صحنهٔ کسب‌وکار")
    }

    // ── Weekly campaign ──────────────────────────────────────────────────

    private fun weeklyFor(
        n: String,
        type: Type,
        goals: Set<Goal>
    ): String {
        val day = if (goals.contains(Goal.SALES)) "پنجشنبه" else "یکشنبه"
        val body = when (type) {
            Type.SHOP -> "🔥 پیشنهاد آخر هفتهٔ $n\n{first_name} جان، فقط تا شنبه:\nتخفیف ویژه روی محصولات منتخب!\nمشاهده: /price — سفارش: /order"
            Type.RESTAURANT -> "🍽 جمع‌بازی هفته در $n\n{first_name} جان، این جمعه میزهای محدود!\nرزرو: /reserve"
            Type.CLINIC -> "🌿 برنامهٔ هفتهٔ آیندهٔ $n منتشر شد.\nنوبت‌های باقی‌مانده محدود است — رزرو: /booking"
            Type.EDUCATION -> "📅 تقویم کلاس‌های هفتهٔ آیندهٔ $n:\nثبت‌نام و جزئیات: /courses"
            Type.GYM -> "💪 برنامهٔ هفتهٔ آیندهٔ $n:\nکلاس‌های جدید اضافه شد — جدول: /classes"
            Type.SERVICES -> "🛠 ظرفیت خالی هفتهٔ آیندهٔ $n:\nزود وقت بگیر — /booking"
            Type.CONTENT -> "📣 بهترین محتواهای هفتهٔ $n را از دست نده!\nمشاهده: /latest"
            Type.OTHER -> "👋 {first_name} جان، خبرهای هفتهٔ $n:\nجدیدترین‌ها: /news"
        }
        return "هر $day ساعت ۱۸:۰۰ این پیام را برای همهٔ مشتریان بفرست:\n\n$body"
    }
}
