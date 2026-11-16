// APEX CLIENT — бот, который отвечает сам (Cloudflare Worker, бесплатный тариф).
// Пульт → Telegram → Настройка → «Бот отвечает сам» — там пошаговая инструкция и кнопка «Скопировать код».
//
// Что умеет:
//   • в личке отвечает на частые вопросы: кнопки-темы и поиск по словам («не подключается», «как обновить»…);
//   • конкурсы: кнопка «🎉 Участвую» под постом в канале — запоминает участников; итоги подводит робот GitHub
//     в назначенное время (или владелец командой /draw <номер> в личке бота).
//
// Настройки Worker (Settings → Variables and Secrets):
//   BOT_TOKEN      — секрет: токен бота от @BotFather;
//   WEBHOOK_SECRET — секрет: любое длинное слово (пульт создаёт его кнопкой) — защита от чужих запросов;
//   OWNER_ID       — по желанию: ваш номер в Telegram (команды /draw, /contests);
//   SUPPORT        — по желанию: ссылка поддержки (по умолчанию https://t.me/Apexx_support).
// Привязка KV (Settings → Bindings → KV namespace) с именем APEX_KV — нужна только для конкурсов.
// После сохранения откройте https://<ваш-worker>.workers.dev/setup?key=<WEBHOOK_SECRET> — бот начнёт отвечать.
//
// Тексты бота (приветствие, темы, слова, ответы, ссылка поддержки) редактируются в пульте:
// Telegram → Настройка → «Тексты бота» → «Сохранить». Бот берёт их из docs/tg/bot.json на сайте
// (обновляет раз в 5 минут) — код в Cloudflare менять не нужно. Нет файла — встроенные тексты ниже.
// BOT_CONFIG — по желанию: свой адрес файла настроек.
//
// 2.37 — функции, которые включаются и выключаются в пульте (Telegram → Настройка → «Функции бота»):
//   /status — работают ли серверы (техработы из пульта); /ping, /stats, /help — для владельца;
//   ответы на английском, если Telegram у человека не на русском; вопрос без ответа — владельцу в личку,
//   его ответ на пересланное сообщение бот отправит человеку; счёт, какие темы спрашивают чаще.

// ЧТО НОВОГО В БОТЕ (пульт показывает это в «Справка» → «Что нового» → «Бот»; V — версия, n/u/f — новое/улучшено/исправлено)
// V|3.4|16 ноября 2026
// u|«🛠 Админ» по разделам: «📊 Сводки и контроль», «👥 Работа с людьми», «📣 Рассылки и канал», «⚙️ Управление ботом»; «📥 Вопросы» и «📊 Сегодня» — сразу сверху
// V|3.3|16 ноября 2026
// u|Функции бота — по разделам: «Админ и сводки», «Ответы людям», «Помощники», «Команды», «Рассылки и канал», «Конкурсы», «Группа», «Защита и прочее»; у раздела видно, сколько включено. В /settings, «⚙️ Функции» и меню сервера
// V|3.2|16 ноября 2026
// n|/report — форма из трёх вопросов (устройство, версия, что не так), в поддержку приходит одной карточкой; /quiz — викторина по приложению; /version — последняя версия
// n|Память разговора: «не помогло» сразу после темы — бот передаёт вопрос людям и подсказывает, какую тему человек уже читал
// V|3.1|16 ноября 2026
// n|Умный поиск: бот понимает вопросы с опечатками («устоновить», «падписка»)
// n|Группа: капча «✅ Я не робот» для новичков (не нажал за 5 минут — удаляется); /warn, /unwarn и /ban ответом на сообщение — на третьем предупреждении бан на сутки
// n|Поздравление с годовщиной: через год после первого сообщения боту
// V|3.0|16 ноября 2026
// n|Человек видит своё место в очереди; ночью бот предупреждает, что поддержка ответит утром; кнопка «🙋 Позвать человека» — следующее сообщение сразу в поддержку
// n|В приветствии — предупреждение о поддельных ботах: официальный только этот, пароли и оплату в личке не просим
// V|2.9|16 ноября 2026
// n|«🛟 Помощники» — до 5 человек отвечают на вопросы кнопками «🛟 Помощник»: без функций, рассылок, банов и данных; новые вопросы — им по очереди; кто сколько ответил за 7 дней
// V|2.8|16 ноября 2026
// n|«📢 Пост в канал» из бота с предпросмотром; «➕ Новый конкурс» — пост в канале с кнопкой «🎉 Участвую», итоги бот подводит сам в назначенный день
// n|«💾 Копия данных» — файл со всеми данными бота вам в личку; «🖥 Система и ошибки» — где работает, сколько без перезапуска, последние ошибки; на своём сервере — «♻️ Перезапустить»
// V|2.7|16 ноября 2026
// n|«📊 Опрос» в «🛠 Админ»: вопрос и варианты одним сообщением, предпросмотр, отправка всем; «📊 Итоги опросов» — голоса и проценты
// n|Шаблон ответа с правкой: «✏️» рядом с шаблоном — бот покажет текст, вы поправите и отправите
// V|2.6|16 ноября 2026
// n|/today — сводка за сутки (вопросы, ждут ответа, новые люди, жалобы, спам); утренняя сводка в 9:00 сама (вкл/выкл); /queue — вопросы без ответа кнопками; /top — частые вопросы и «➕ Сделать темой» из отвеченного вопроса: бот сам отвечает так же на похожие
// V|2.5|16 ноября 2026
// n|👥 Пользователи: сколько, список по страницам, поиск по номеру, @нику и имени, карточка (когда писал, вопросы, «Помогло?», приглашения), кнопки — написать, забанить и разбанить, отписать от рассылок, удалить данные; /user номер, /users. То же — в пульте
// V|2.4|16 ноября 2026
// n|Панель админа на кнопках: кнопка «🛠 Админ» внизу (только у владельца) — сегодня, вопросы (ответить, шаблон, решено, заблокировать), рассылка с предпросмотром, функции, серверы, конкурсы с итогами, статистика, заблокированные, проверка бота
// V|2.3|16 ноября 2026
// f|Кнопки владельца (/settings, быстрые ответы) у других людей молча не работают — без ответа и подсказок; команды владельца у них не видны в меню «/» и отвечают «такой команды нет»
// V|2.2|16 ноября 2026
// f|Cloudflare: рассылка, «Помогло?» и меню команд работают по расписанию раз в минуту, а не только когда боту кто-то пишет (после «Применить к боту в Cloudflare»)
// V|2.1|16 ноября 2026
// n|/settings — владелец включает и выключает любую функцию кнопками прямо в боте; то же — в меню сервера («Функции бота»)
// n|Новые переключатели: фото и голосовые, быстрые ответы, меню команд, картинки, ссылки на темы, опросы, поздравление победителей
// u|Что переключили позже — в пульте, в боте или в меню, — то и действует
// V|2.0|16 ноября 2026
// n|Рассылка и опросы в личку всем, кто писал боту (из пульта); /stop — не получать
// n|Напоминание о продлении подписки — для тех, кто включил /remind
// n|Группа: приветствие новичков с кнопками и защита от ссылок-спама в первые сутки
// n|Фото, голосовые и файлы — вам в личку; быстрые ответы кнопками под пересланным вопросом
// n|«Пригласи друга»: /invite — своя ссылка и счёт приглашённых
// n|Конкурс: только подписчики канала; по желанию — без пустых аккаунтов (нет фото и @имени); победителям — поздравление в личку
// n|Меню команд «/», описание бота из пульта, картинки в ответах, ссылки на тему (t.me/бот?start=тема)
// n|Через день после ответа — «Помогло?»; жалобы на сервер («не работает Германия») — вам, если их несколько за час
// n|Перевод вашего ответа на английский для англоязычных; защита от флуда; «у меня 3.20» — подскажет новую версию
// n|Данные для пульта: вопросы, статистика, опросы, приглашения — зашифрованы, забирает робот GitHub
// n|/download — свежая версия: кнопка на сайт и запасная ссылка (файл 70 МБ больше лимита Telegram для ботов)
// V|1.4|16 ноября 2026
// f|Пересылка вопросов владельцу: не больше 3 в час от одного человека — личку не завалят спамом
// V|1.3|16 ноября 2026
// n|/status — любой может узнать, есть ли техработы на серверах (берётся из пульта)
// n|Ответы на английском, если Telegram у человека не на русском
// n|Вопрос без готового ответа — владельцу в личку; ответ на пересланное сообщение бот отправит человеку
// n|/stats — какие темы спрашивают чаще за неделю; /ping и /help — для владельца
// n|Каждую функцию можно включить или выключить в пульте: «Тексты и функции бота»
// u|На своём сервере: прокси для Telegram, уведомления владельцу о запуске и остановке
// V|1.2|16 ноября 2026
// n|Бот работает и на своём сервере (Ubuntu) — без Cloudflare, домена и HTTPS; конкурсы хранятся на сервере
// V|1.1|14 ноября 2026
// n|Приветствие, темы, слова и ответы бота редактируются в пульте — код менять не нужно
// u|В текстах можно писать {сайт} и {поддержка} — подставятся ссылки
// V|1.0|14 ноября 2026
// n|Бот отвечает в личке на частые вопросы: кнопки-темы и поиск по словам
// n|Конкурсы: кнопка «🎉 Участвую» под постом, честный выбор победителей, /draw и /contests для владельца
const SITE = 'https://dubstep2004.github.io'
const CONFIG_URL = SITE + '/apex-client/tg/bot.json'

const FAQ = [
  { id: 'install', title: '📥 Как установить', words: ['установ', 'скача', 'скачать', 'apk', 'где взять', 'загруз'],
    text: 'Как установить APEX CLIENT:\n1. Откройте на телефоне сайт ' + SITE + ' и нажмите «Скачать для телефона».\n2. Откройте скачанный файл и нажмите «Установить» (первый раз телефон попросит разрешить установку из браузера).\n3. Вставьте ссылку подписки и нажмите на щит.\n\nВидео за минуту: ' + SITE + '/update/' },
  { id: 'connect', title: '🔌 Не подключается', words: ['не подключ', 'не работает', 'не грузит', 'ошибка', 'не включ', 'не коннект', 'не заход'],
    text: 'Если не подключается:\n1. Откройте в приложении «Проверка сети» (кнопка на главном) — она покажет, что не так: нет интернета, белые списки или глушат VPN, и подскажет, что сделать.\n2. Включите автопилот — он сам выберет рабочий сервер.\n3. Обновите подписку: Настройки → Подписка → «Обновить».\n\nНе помогло — напишите в поддержку и приложите итог проверки (кнопка «Поделиться»).' },
  { id: 'update', title: '⬆️ Как обновить', words: ['обнов', 'новая версия', 'версия', 'update'],
    text: 'Обновление приходит само: на главном появится плашка «Вышла версия» или окно «Доступна версия» — нажмите «Обновить». Приложение скачает файл, проверит подпись, а Android спросит «Обновить это приложение?».\nНастройки и подписка сохранятся.\n\nКак это выглядит: ' + SITE + '/update/' },
  { id: 'slow', title: '🐢 Медленно', words: ['медлен', 'скорост', 'тормоз', 'лагает', 'пинг', 'долго'],
    text: 'Если медленно:\n• «Тест скорости» в приложении — сравните с VPN и без;\n• выберите режим «Скорость» или «Игры»;\n• попробуйте другой сервер (автопилот выбирает самый быстрый);\n• на мобильном с белыми списками работает только «Обход LTE» — он медленнее обычных серверов.' },
  { id: 'whitelist', title: '📶 Белые списки и глушилка', words: ['белые списки', 'белый список', 'глуш', 'lte', 'мобильн', 'оператор'],
    text: 'Когда оператор включает белые списки или глушит VPN, автопилот сам переходит на «Обход LTE» или рабочий протокол. Проверить, что происходит: кнопка «Проверка сети» на главном экране. Отключить автопереход: Настройки → Профиль.' },
  { id: 'support', title: '💬 Поддержка и продление', words: ['поддерж', 'продл', 'оплат', 'подписк', 'купить', 'цена', 'админ', 'человек'],
    text: null },
]

const FAQ_EN = [
  { id: 'install', title: '📥 How to install', words: ['install', 'download', 'apk', 'get the app'],
    text: 'How to install APEX CLIENT:\n1. Open ' + SITE + '/en/ on your phone and tap «Download».\n2. Open the downloaded file and tap «Install» (the first time Android asks to allow installs from the browser).\n3. Paste your subscription link and tap the shield.' },
  { id: 'connect', title: '🔌 Can\'t connect', words: ['connect', 'not work', "doesn't work", 'error', 'broken', 'fail'],
    text: 'If it does not connect:\n1. Open «Network check» on the home screen — it shows what is wrong and what to do.\n2. Turn on Autopilot — it picks a working server.\n3. Refresh the subscription: Settings → Subscription → «Refresh».' },
  { id: 'update', title: '⬆️ How to update', words: ['update', 'new version', 'upgrade'],
    text: 'Updates arrive by themselves: a «New version» banner or an «Update available» window appears — tap «Update». Settings and subscription are kept.' },
  { id: 'slow', title: '🐢 Slow', words: ['slow', 'speed', 'lag', 'ping'],
    text: 'If it is slow: run «Speed test» in the app, choose the «Speed» or «Games» mode, or try another server — Autopilot picks the fastest one.' },
  { id: 'support', title: '💬 Support', words: ['support', 'help', 'pay', 'renew', 'subscription', 'price', 'human'],
    text: 'Support will help with connection, subscription and renewal: {поддержка}' },
]
const EN = { greeting: 'Hi! I am the APEX CLIENT bot 👋\nChoose a topic or just type your question.', notFound: 'I could not find a ready answer 🤔 Choose a topic or contact support.', other: '📋 Other topics', support: '💬 Contact support', pick: 'Choose a topic:', sent: 'Your question has been sent to support — the answer will come here 🙌' }
const RU_LANGS = /^(ru|uk|be|kk|uz|ky|tg|hy|az|ka)/
const isEn = (cfg, u) => cfg.features.english && !!u?.language_code && !RU_LANGS.test(u.language_code)

const json = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { 'content-type': 'application/json' } })

// ── Хранилище: JSON в KV (Cloudflare) или в файле (свой сервер)
const kget = async (env, k, d) => { try { const v = await env.APEX_KV?.get(k); return v ? JSON.parse(v) : d } catch { return d } }
const kput = (env, k, v, ttl) => env.APEX_KV?.put(k, JSON.stringify(v), ttl ? { expirationTtl: ttl } : undefined).catch?.(() => {})
const now = () => Date.now()

async function tg(env, method, body) {
  const r = await fetch(`https://api.telegram.org/bot${env.BOT_TOKEN}/${method}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) })
  return r.json()
}
async function tgFile(env, chat, name, content, caption) { // отправить файл: тело multipart собираем строкой (так работает и через прокси на сервере)
  const b = 'apex' + now().toString(36) + Math.random().toString(36).slice(2), part = (k, v) => `--${b}\r\ncontent-disposition: form-data; name="${k}"\r\n\r\n${v}\r\n`
  const body = part('chat_id', chat) + (caption ? part('caption', caption) : '') + `--${b}\r\ncontent-disposition: form-data; name="document"; filename="${name}"\r\ncontent-type: application/json\r\n\r\n${content}\r\n--${b}--\r\n`
  const r = await fetch(`https://api.telegram.org/bot${env.BOT_TOKEN}/sendDocument`, { method: 'POST', headers: { 'content-type': 'multipart/form-data; boundary=' + b }, body: new TextEncoder().encode(body) })
  return r.json()
}
// Журнал ошибок: на своём сервере — в памяти (server.js), на Cloudflare — в KV, последние 20, хранится неделю
async function logErr(env, where, e) {
  const x = { at: now(), where, msg: String(e?.message || e).slice(0, 200) }
  if (Array.isArray(env.ERRLOG)) { env.ERRLOG.push(x); env.ERRLOG.splice(0, env.ERRLOG.length - 20); return }
  try { const l = await kget(env, 'errlog', []), last = l[l.length - 1]; if (last && last.msg === x.msg && x.at - last.at < 600000) return; l.push(x); await kput(env, 'errlog', l.slice(-20), 7 * 86400) } catch {} // одинаковая ошибка — не чаще раза в 10 минут (лимит записей KV)
}
const errList = async env => Array.isArray(env.ERRLOG) ? env.ERRLOG : await kget(env, 'errlog', [])
const mskTime = t => new Date(t + 3 * 3600000).toISOString().slice(5, 16).replace('T', ' ').replace(/^(\d\d)-(\d\d)/, '$2.$1')

// Функции: по умолчанию ← пульт (bot.json) ← переключено в боте (/settings) или в меню сервера (KV «fover», с временем).
// Что новее — то и действует: сохранили в пульте позже — пульт главнее.
async function mergeFeatures(env, c) {
  const f = Object.fromEntries(Object.entries(FEATURES).map(([k, d]) => [k, typeof c?.features?.[k] === 'boolean' ? c.features[k] : d]))
  const ov = await kget(env, 'fover', {}), pAt = +c?.featuresAt || 0
  for (const [k, o] of Object.entries(ov)) if (k in f && typeof o?.v === 'boolean' && (o.at || 0) > pAt) f[k] = o.v
  return f
}
async function setFeature(env, k, v) { const ov = await kget(env, 'fover', {}); ov[k] = { v, at: now() }; await kput(env, 'fover', ov); cfgCache = null }
// Разделы функций: /settings, «⚙️ Функции» в «🛠 Админ» и меню сервера показывают функции по разделам
const FEAT_GROUPS = [
  ['adm', '🛠 Админ и сводки', ['admin', 'morning', 'stats', 'syslog', 'backup']],
  ['ans', '💬 Ответы людям', ['forward', 'media', 'quick', 'followup', 'translate', 'english', 'qpos', 'night', 'human', 'memory', 'form', 'fuzzy']],
  ['hlp', '🛟 Помощники', ['helpers', 'helpRR']],
  ['cmd', '📋 Команды', ['commands', 'status', 'news', 'invite', 'remind', 'complaints', 'version', 'ping', 'quiz', 'deeplinks', 'images']],
  ['bc', '📣 Рассылки и канал', ['broadcast', 'polls', 'chpost']],
  ['con', '🎁 Конкурсы', ['contestBot', 'contestSub', 'contestReal', 'contestDM']],
  ['grp', '👥 Группа', ['welcome', 'antispam', 'captcha', 'warns']],
  ['etc', '🛡 Защита и прочее', ['antiflood', 'fakewarn', 'anniv']]]
const featGroups = () => { const used = new Set(FEAT_GROUPS.flatMap(g => g[2])), rest = Object.keys(FEAT_NAMES).filter(k => !used.has(k)); return rest.length ? [...FEAT_GROUPS, ['oth', '📦 Другое', rest]] : FEAT_GROUPS }
const groupOf = k => featGroups().find(g => g[2].includes(k))?.[0]
const settingsKb = (cfg, sec) => { // без раздела — список разделов с числом включённых; с разделом — переключатели
  const gs = featGroups().map(g => [g[0], g[1], g[2].filter(k => k in cfg.features && FEAT_NAMES[k])])
  if (!sec) return { inline_keyboard: [...gs.filter(g => g[2].length).map(g => [{ text: `${g[1]} · ${g[2].filter(k => cfg.features[k]).length}/${g[2].length}`, callback_data: 'fs:' + g[0] }]), ...(cfg.features.admin ? [[{ text: '⬅️ В «🛠 Админ»', callback_data: 'ad:menu' }]] : [])] }
  const g = gs.find(x => x[0] === sec) || gs[0]
  return { inline_keyboard: [...g[2].map(k => [{ text: (cfg.features[k] ? '✅ ' : '▫️ ') + FEAT_NAMES[k], callback_data: 'ft:' + k }]), [{ text: '⬅️ Разделы', callback_data: 'fs:' }]] }
}

// Настройки из пульта: кэш на 5 минут в памяти Worker; файл не открылся — встроенные тексты
let cfgCache = null, cfgAt = 0
async function config(env) {
  if (cfgCache && Date.now() - cfgAt < 300000) return cfgCache
  let c = null
  try {
    const r = await fetch((env.BOT_CONFIG && env.BOT_CONFIG.startsWith('https://')) ? env.BOT_CONFIG : CONFIG_URL, { cf: { cacheTtl: 300 } })
    if (r.ok) c = await r.json()
  } catch {}
  const topics = Array.isArray(c?.topics) ? c.topics.filter(t => t && t.id && t.title && t.text).slice(0, 12).map(t => ({
    id: String(t.id).replace(/[^\w-]/g, '').slice(0, 24), title: String(t.title).slice(0, 40),
    words: (Array.isArray(t.words) ? t.words : []).map(w => String(w).toLowerCase().trim()).filter(Boolean).slice(0, 30), text: String(t.text).slice(0, 3500),
  })) : []
  cfgCache = {
    greeting: (c?.greeting && String(c.greeting).slice(0, 1000)) || 'Привет! Я бот APEX CLIENT 👋\nВыберите тему или просто напишите вопрос своими словами.',
    notFound: (c?.notFound && String(c.notFound).slice(0, 1000)) || 'Не нашёл готового ответа 🤔 Выберите тему или напишите в поддержку — там помогут.',
    support: (c?.support && String(c.support).startsWith('https://')) ? String(c.support) : ((env.SUPPORT && env.SUPPORT.startsWith('https://')) ? env.SUPPORT : 'https://t.me/Apexx_support'),
    topics: [...(topics.length ? topics : FAQ.map(f => ({ ...f, text: f.text || 'Поддержка поможет с подключением, подпиской и продлением: {поддержка}' }))), ...(await kget(env, 'ktopics', [])).slice(0, 12)],
    // Функции бота: пульт → «Функции бота». Нет настройки — значение по умолчанию
    features: await mergeFeatures(env, c),
    // 2.0: быстрые ответы владельца, описание бота, рассылка/опрос, картинки к темам
    quick: (Array.isArray(c?.quick) ? c.quick : QUICK).map(x => String(x).slice(0, 300)).filter(Boolean).slice(0, 6),
    description: String(c?.description || '').slice(0, 512), short: String(c?.short || '').slice(0, 120),
    broadcast: c?.broadcast && /^[\w-]{4,40}$/.test(String(c.broadcast.id || '')) ? c.broadcast : null,
    images: Object.fromEntries((Array.isArray(c?.topics) ? c.topics : []).filter(t => t?.id && /^https:\/\/\S+$/.test(t.image || '')).map(t => [String(t.id).replace(/[^\w-]/g, '').slice(0, 24), t.image])),
    channel: /^@?\w{4,}$/.test(String(c?.channel || '')) ? String(c.channel) : '', hasOwner: !!env.OWNER_ID,
    welcome: String(c?.welcome || 'Добро пожаловать, {имя}! 👋\nЗдесь новости и помощь по APEX CLIENT.').slice(0, 600),
  }
  cfgAt = Date.now()
  return cfgCache
}
const FEATURES = { memory: true, form: true, quiz: true, fuzzy: true, captcha: false, warns: true, anniv: true, qpos: true, night: true, human: true, fakewarn: true, helpers: true, helpRR: true, chpost: true, contestBot: true, backup: true, syslog: true, morning: true, admin: true, media: true, quick: true, commands: true, images: true, deeplinks: true, contestDM: true, polls: true, broadcast: true, news: true, status: true, ping: true, english: true, forward: false, stats: true, welcome: true, antispam: true, invite: true, remind: true, followup: true, complaints: true, translate: true, antiflood: true, version: true, contestSub: true, contestReal: false }
// Названия функций — для /settings в боте и меню сервера (apex → «Функции бота»)
const FEAT_NAMES = { admin: 'Панель админа на кнопках', morning: 'Утренняя сводка в 9:00', chpost: 'Пост в канал из бота', helpers: 'Помощники', qpos: 'Место в очереди', fuzzy: 'Умный поиск с опечатками', memory: 'Память разговора', form: 'Форма /report', quiz: 'Викторина /quiz', captcha: 'Капча для новичков в группе', warns: '/warn и /ban в группе', anniv: 'Поздравление с годовщиной', night: 'Ночью — «ответим утром»', human: '«Позвать человека»', fakewarn: 'Предупреждение о поддельных ботах', helpRR: 'Помощникам — вопросы по очереди', contestBot: 'Конкурс из бота', backup: 'Копия данных файлом', syslog: 'Система и ошибки', forward: 'Вопросы без ответа — мне', media: 'Фото и голосовые — мне', quick: 'Быстрые ответы', followup: '«Помогло?»', translate: 'Перевод моих ответов', english: 'Ответы на английском', status: '/status', news: '/news', invite: '/invite', remind: '/remind', complaints: 'Жалобы на серверы', version: 'Подсказка версии', broadcast: 'Рассылка', polls: 'Опросы', stats: 'Счёт тем', ping: '/ping', commands: 'Меню команд «/»', images: 'Картинки в ответах', deeplinks: 'Ссылки на темы', antiflood: 'Защита от флуда', welcome: 'Приветствие в группе', antispam: 'Антиспам в группе', contestSub: 'Конкурс: только подписчики', contestReal: 'Конкурс: без пустых аккаунтов', contestDM: 'Конкурс: поздравить победителей' }
const QUICK = ['Обновите подписку: Настройки → Подписка → «Обновить»', 'Включите автопилот — он сам выберет рабочий сервер', 'Откройте «Проверка сети» на главном и пришлите итог (кнопка «Поделиться»)', 'Обновите приложение до последней версии: ' + SITE + '/update/']
const fill = (cfg, t) => t.replaceAll('{сайт}', SITE).replaceAll('{поддержка}', cfg.support)
const topicsOf = (cfg, en) => en ? FAQ_EN : cfg.topics
const menu = (cfg, en) => ({ inline_keyboard: topicsOf(cfg, en).map(f => [{ text: f.title, callback_data: (en ? 'faqen:' : 'faq:') + f.id }]) })
const after = (cfg, en) => ({ inline_keyboard: [[{ text: en ? EN.other : '📋 Другие темы', callback_data: en ? 'menuen' : 'menu' }], ...(cfg.features.human && cfg.features.forward && cfg.hasOwner ? [[{ text: en ? '🙋 Ask a human' : '🙋 Позвать человека', callback_data: 'human' }]] : []), [{ text: en ? EN.support : '💬 Написать в поддержку', url: cfg.support }]] })
const isNight = () => { const h = new Date(now() + 3 * 3600000).getUTCHours(); return h < 9 } // 0:00–8:59 МСК
async function fakeWarn(env, cfg, en) { if (!cfg.features.fakewarn) return ''; const n = await botName(env); return n ? (en ? `\n\n🛡 The only official bot is @${n}. Support never asks for passwords, SMS codes or payment in private messages.` : `\n\n🛡 Официальный бот — только @${n}. Поддержка никогда не просит пароль, код из СМС или оплату в личке.`) : '' }

// Счёт тем: st:<день> → { тема: число, _miss: без ответа } — для /stats (хранится 8 дней)
async function count(env, cfg, id) {
  if (!cfg.features.stats || !env.APEX_KV) return
  try {
    const day = new Date().toISOString().slice(0, 10), k = 'st:' + day
    const o = JSON.parse(await env.APEX_KV.get(k) || '{}'); o[id] = (o[id] || 0) + 1
    await env.APEX_KV.put(k, JSON.stringify(o), { expirationTtl: 8 * 86400 })
  } catch {}
}
async function statsText(env, cfg) {
  if (!env.APEX_KV) return 'Нет хранилища APEX_KV — статистика не ведётся'
  const sum = {}
  for (let i = 0; i < 7; i++) {
    const day = new Date(Date.now() - i * 86400000).toISOString().slice(0, 10)
    Object.entries(JSON.parse(await env.APEX_KV.get('st:' + day) || '{}')).forEach(([k, v]) => { sum[k] = (sum[k] || 0) + v })
  }
  const title = id => (cfg.topics.find(t => t.id === id) || FAQ_EN.find(t => t.id === id) || {}).title || id
  const rows = Object.entries(sum).filter(([k]) => k !== '_miss').sort((a, b) => b[1] - a[1]).map(([k, v], i) => `${i + 1}. ${title(k)} — ${v}`)
  return '📊 Вопросы за 7 дней\n\n' + (rows.join('\n') || 'Пока нет') + (sum._miss ? `\n\nБез готового ответа: ${sum._miss}` : '') + (cfg.features.stats ? '' : '\n\n(счёт выключен в пульте)')
}

// /status: техработы и пометки серверов из пульта (docs/config/app.json на сайте)
async function statusText() {
  let st = {}
  try { const r = await fetch(SITE + '/apex-client/config/app.json', { cf: { cacheTtl: 120 } }); if (r.ok) st = (await r.json())?.servers?.status || {} } catch {}
  const now = Date.now(), rows = []
  for (const [name, v] of Object.entries(st)) {
    if (!v || typeof v !== 'object') continue
    const until = v.until ? ' до ' + new Date(v.until).toLocaleString('ru', { timeZone: 'Europe/Moscow', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) + ' МСК' : ''
    if (v.state === 'maintenance' && (!v.from || v.from <= now) && (!v.until || v.until > now)) rows.push('🔧 ' + name + ' — техработы' + until)
    else if (v.state === 'maintenance' && v.from > now) rows.push('🗓 ' + name + ' — техработы с ' + new Date(v.from).toLocaleString('ru', { timeZone: 'Europe/Moscow', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) + ' МСК')
    else if (v.state === 'slow' && (!v.until || v.until > now)) rows.push('🐢 ' + name + ' — может работать медленно' + until)
  }
  return (rows.length ? '🛰 Состояние серверов\n\n' + rows.join('\n') : '✅ Все серверы работают в обычном режиме') + '\n\nПодробнее: ' + SITE + '/status/'
}

function findFaq(cfg, text, en) {
  const t = (text || '').toLowerCase().replaceAll('ё', 'е')
  let best = null, score = 0
  for (const f of topicsOf(cfg, en)) {
    const s = f.words.filter(w => t.includes(w.replaceAll('ё', 'е'))).length
    if (s > score) { best = f; score = s }
  }
  if (best || !cfg.features.fuzzy) return best
  // Умный поиск: слова с опечаткой («устоновить», «падписка») — начало слова совпадает с точностью до 1–2 букв
  const words = t.split(/[^\p{L}\d]+/u).filter(w => w.length >= 4)
  for (const f of topicsOf(cfg, en)) {
    const s = f.words.filter(k => k.length >= 5 && words.some(w => lev(w.slice(0, k.length + 1), k) <= (k.length >= 7 ? 2 : 1) || lev(w.slice(0, k.length), k) <= (k.length >= 7 ? 2 : 1))).length
    if (s > score) { best = f; score = s }
  }
  return best
}
function lev(a, b) { // расстояние между словами (сколько букв поменять)
  if (Math.abs(a.length - b.length) > 2) return 9
  let p = Array.from({ length: b.length + 1 }, (_, i) => i)
  for (let i = 1; i <= a.length; i++) { const c = [i]; for (let j = 1; j <= b.length; j++) c[j] = Math.min(p[j] + 1, c[j - 1] + 1, p[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1)); p = c }
  return p[b.length]
}

// ── 2.0: пользователи бота (для рассылки), флуд, версии, новости, перевод
const flood = new Map() // в памяти: uid → [время сообщений за минуту]
function flooded(uid) {
  const t = now(), a = (flood.get(uid) || []).filter(x => t - x < 60000); a.push(t); flood.set(uid, a)
  if (flood.size > 5000) flood.clear()
  return a.length > 8 ? (a.length === 9 ? 'warn' : 'skip') : ''
}
async function remember(env, u) { // кто писал боту — для рассылки (без /stop) и карточка человека для админа
  const users = await kget(env, 'users', [])
  if (!users.includes(u.id) && !(await kget(env, 'stop', [])).includes(u.id)) { users.push(u.id); await kput(env, 'users', users.slice(-50000)) }
  // Карточка: пишем не чаще раза в 6 часов (бесплатный Cloudflare KV — 1000 записей в день)
  const k = 'u:' + u.id, c = await kget(env, k, null)
  if (!c || now() - (c.last || 0) > 6 * 3600000 || c.name !== (u.first_name || '') || c.un !== (u.username || '')) {
    await kput(env, k, { id: u.id, name: [u.first_name, u.last_name].filter(Boolean).join(' ').slice(0, 40), un: u.username || '', lang: u.language_code || '', first: c?.first || now(), last: now(), visits: (c?.visits || 0) + 1 })
  }
}
async function userCard(env, id) { // текст карточки и кнопки управления
  const c = await kget(env, 'u:' + id, null), q = (await kget(env, 'q', [])).filter(x => +x.uid === +id), fb = (await kget(env, 'fbk', [])).filter(f => q.some(x => x.id === f.qid))
  const blocked = (await kget(env, 'block', [])).includes(+id), stopped = (await kget(env, 'stop', [])).includes(+id), refc = (await kget(env, 'refc', {}))[id] || 0, ref = await kget(env, 'ref:' + id, null)
  const d = t => t ? new Date(t).toLocaleString('ru', { timeZone: 'Europe/Moscow', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'
  const text = `👤 ${c?.name || 'без имени'}${c?.un ? ' @' + c.un : ''}\nНомер: ${id}${c?.lang ? ' · язык ' + c.lang : ''}\n\nВпервые: ${d(c?.first)}\nПоследний раз: ${d(c?.last)}\nВопросов в поддержку: ${q.length} (без ответа: ${q.filter(x => !x.ans).length})\n«Помогло?»: 👍 ${fb.filter(f => f.ok).length} · 👎 ${fb.filter(f => !f.ok).length}\nПригласил: ${refc}${ref ? ' · пришёл по приглашению ' + ref : ''}\n\n${blocked ? '🚫 Заблокирован' : '✅ Может писать боту'} · ${stopped ? '🔕 без рассылок' : '🔔 получает рассылки'}`
  const rows = [[{ text: '✉️ Написать', callback_data: 'ad:um:' + id }, blocked ? { text: '✅ Разбанить', callback_data: 'ad:uu:' + id } : { text: '🚫 Забанить', callback_data: 'ad:ub2:' + id }],
    [stopped ? { text: '🔔 Вернуть рассылки', callback_data: 'ad:us:' + id } : { text: '🔕 Отписать от рассылок', callback_data: 'ad:us:' + id }, { text: '🗑 Удалить данные', callback_data: 'ad:ud:' + id }], [{ text: '⬅️ К пользователям', callback_data: 'ad:users' }]]
  return { text, rows }
}
async function userAction(env, act, id, text) { // общее для бота и пульта: ban / unban / stop / delete / msg
  id = +id; if (!id) return { error: 'нет номера' }
  if (act === 'ban') { const b = await kget(env, 'block', []); if (!b.includes(id)) b.push(id); await kput(env, 'block', b.slice(-5000)) }
  else if (act === 'unban') await kput(env, 'block', (await kget(env, 'block', [])).filter(x => x !== id))
  else if (act === 'stop') { const st = await kget(env, 'stop', []), on = st.includes(id); await kput(env, 'stop', on ? st.filter(x => x !== id) : [...st, id].slice(-50000)); const u = await kget(env, 'users', []); if (!on) await kput(env, 'users', u.filter(x => x !== id)); else if (!u.includes(id)) { u.push(id); await kput(env, 'users', u) } }
  else if (act === 'delete') { await env.APEX_KV?.delete('u:' + id); await kput(env, 'users', (await kget(env, 'users', [])).filter(x => x !== id)); await kput(env, 'q', (await kget(env, 'q', [])).filter(x => +x.uid !== id)) }
  else if (act === 'msg') { const r = await tg(env, 'sendMessage', { chat_id: id, text: String(text || '').slice(0, 4000), disable_web_page_preview: true }); return r.ok ? { ok: true } : { error: r.description || 'не отправилось' } }
  else return { error: 'неизвестное действие' }
  return { ok: true }
}
async function userList(env, n = 300) { // последние n человек — для пульта и поиска
  const ids = (await kget(env, 'users', [])).slice(-n).reverse(), out = []
  const extra = [...(await kget(env, 'block', [])), ...(await kget(env, 'stop', []))].filter(x => !ids.includes(x)).slice(-50)
  for (const id of [...ids, ...extra]) { const c = await kget(env, 'u:' + id, null); out.push(c || { id }) }
  return out
}
let latest = { v: '', at: 0 }
async function latestVersion() {
  if (now() - latest.at < 600000 && latest.v) return latest.v
  try { const r = await fetch('https://api.github.com/repos/Dubstep2004/apex-client/releases/latest', { headers: { 'user-agent': 'APEX-bot', accept: 'application/vnd.github+json' } }); if (r.ok) latest = { v: String((await r.json()).tag_name || '').replace(/^v/, ''), at: now() } } catch {}
  return latest.v
}
const vcmp = (a, b) => { const x = a.split('.').map(Number), y = b.split('.').map(Number); for (let i = 0; i < 3; i++) if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) - (y[i] || 0); return 0 }
async function newsText(cfg) {
  try {
    const st = await (await fetch(SITE + '/apex-client/stats/tg-state.json', { cf: { cacheTtl: 300 } })).json()
    const ch = (cfg.channel || '').replace(/^@/, ''), posts = (st.views || []).slice(-3).reverse()
    if (!posts.length) return '📰 Новостей пока нет'
    return '📰 Последние новости\n\n' + posts.map(p => '• ' + p.text + (ch ? '\nhttps://t.me/' + ch + '/' + p.id : '')).join('\n\n')
  } catch { return '📰 Новости не загрузились — загляните в канал' }
}
async function translate(env, text) { // русский → английский: Workers AI (Cloudflare) или CF_ACCOUNT_ID + CF_AI_TOKEN (свой сервер)
  const body = { text, source_lang: 'russian', target_lang: 'english' }
  try {
    if (env.AI) return (await env.AI.run('@cf/meta/m2m100-1.2b', body))?.translated_text || ''
    if (env.CF_ACCOUNT_ID && env.CF_AI_TOKEN) {
      const r = await fetch(`https://api.cloudflare.com/client/v4/accounts/${env.CF_ACCOUNT_ID}/ai/run/@cf/meta/m2m100-1.2b`, { method: 'POST', headers: { authorization: 'Bearer ' + env.CF_AI_TOKEN, 'content-type': 'application/json' }, body: JSON.stringify(body) })
      return (await r.json())?.result?.translated_text || ''
    }
  } catch {}
  return ''
}
let meName = ''
async function botName(env) { if (!meName) { const r = await tg(env, 'getMe', {}); meName = r.result?.username || '' } return meName }

// Вопросы для пульта: q → последние 100 {id, uid, name, text, at, kind, ans}
async function logQuestion(env, q) { const l = await kget(env, 'q', []); l.push(q); await kput(env, 'q', l.slice(-100)) }
async function markAnswered(env, fwid, how, text, by) { const l = await kget(env, 'q', []), q = l.find(x => x.id === fwid); if (q) { q.ans = how; q.ansAt = now(); if (text) q.ansText = String(text).slice(0, 1500); if (by) q.by = String(by).slice(0, 40); await kput(env, 'q', l) } return q }

// Помощники: до 5 человек отвечают на вопросы кнопками «🛟 Помощник» — без функций, рассылок, банов и данных
const HELP_KB = { keyboard: [[{ text: '🛟 Помощник' }]], resize_keyboard: true, is_persistent: true }
const HELPER_ACTS = new Set(['menu', 'q', 'qo', 'qa', 'qt', 'qe', 'qd', 'hme'])
async function isHelper(env, cfg, uid) { return !!(cfg.features.helpers && uid && (await kget(env, 'helpers', [])).some(h => +h.id === +uid)) }
async function helperMenu(env, chat, edit, uid) {
  const q = (await kget(env, 'q', [])).filter(x => !x.ans).length
  const text = `🛟 Вы помощник поддержки\n\nЖдут ответа: ${q}\nОтвечайте кнопками — бот отправит ответ человеку от имени поддержки.`, reply_markup = { inline_keyboard: [[{ text: '📥 Вопросы', callback_data: 'ad:q' }, { text: '📈 Мои ответы', callback_data: 'ad:hme' }]] }
  return edit ? tg(env, 'editMessageText', { chat_id: chat, message_id: edit, text, reply_markup }) : tg(env, 'sendMessage', { chat_id: chat, text, reply_markup })
}
async function answeredBy(env, days = 7) { const by = {}; for (const x of await kget(env, 'q', [])) if (x.ans && x.ansAt > now() - days * 86400000) { const k = x.by || (x.ans === 'пульт' ? 'пульт' : 'владелец'); by[k] = (by[k] || 0) + 1 } return by }
async function notifyHelper(env, cfg, q) { // очередь: новый вопрос — следующему помощнику по кругу
  if (!cfg.features.helpers || !cfg.features.helpRR) return
  const hs = await kget(env, 'helpers', []); if (!hs.length) return
  const i = (await kget(env, 'hrr', 0)) % hs.length; await kput(env, 'hrr', i + 1)
  await tg(env, 'sendMessage', { chat_id: hs[i].id, text: `📥 Ваша очередь: вопрос от ${q.name}\n\n${(q.text || '(' + q.kind + ')').slice(0, 300)}`, reply_markup: { inline_keyboard: [[{ text: '✍️ Открыть', callback_data: 'ad:qo:' + q.id }]] } })
}

async function forwardToOwner(env, cfg, m, en, kind) {
  if (!(cfg.features.forward && env.OWNER_ID && env.APEX_KV && await fwAllowed(env, m.from?.id))) return false
  const fw = await tg(env, 'forwardMessage', { chat_id: env.OWNER_ID, from_chat_id: m.chat.id, message_id: m.message_id })
  if (!fw.ok) return false
  const id = fw.result.message_id
  await env.APEX_KV.put('fw:' + id, String(m.chat.id), { expirationTtl: 14 * 86400 })
  const qrec = { id, uid: m.chat.id, name: who({ username: m.from?.username, name: m.from?.first_name }), text: (m.text || m.caption || '').slice(0, 500), kind, at: now(), en: !!en }
  await logQuestion(env, qrec); await notifyHelper(env, cfg, qrec)
  const kb = cfg.features.quick ? cfg.quick.map((q, i) => [{ text: '⚡ ' + q.slice(0, 40), callback_data: `qr:${i}:${id}` }]) : []
  await tg(env, 'sendMessage', { chat_id: env.OWNER_ID, reply_to_message_id: id, text: (kind === 'text' ? '❓ Вопрос без готового ответа' : '📎 Прислали ' + kind) + ' от ' + who({ username: m.from?.username, name: m.from?.first_name }) + (en ? ' (пишет по-английски — ответ переведу)' : '') + '. Ответьте на пересланное сообщение или нажмите быстрый ответ.', reply_markup: { inline_keyboard: kb } })
  const wq = (await kget(env, 'q', [])).filter(x => !x.ans).length, extra = (cfg.features.qpos && wq > 1 ? (en ? `\nYou are #${wq} in the queue.` : `\nВы в очереди: ${wq}-й.`) : '') + (cfg.features.night && isNight() ? (en ? '\n🌙 It is night in Moscow now — support will answer in the morning (from 9:00).' : '\n🌙 Сейчас ночь — поддержка ответит утром (с 9:00 МСК).') : '')
  await tg(env, 'sendMessage', { chat_id: m.chat.id, text: (en ? EN.sent : 'Передал ' + (kind === 'text' ? 'ваш вопрос' : kind) + ' в поддержку — ответ придёт сюда 🙌') + extra, reply_markup: menu(cfg, en) })
  return true
}
async function sendToUser(env, cfg, to, text, fwid, by) { // ответ владельца человеку: перевод для англоязычных + «Помогло?» через день
  const q = (await kget(env, 'q', [])).find(x => x.id === fwid)
  let out = text
  if (q?.en && cfg.features.translate && /[а-яё]/i.test(text)) { const t = await translate(env, text); if (t) out = t }
  const r = await tg(env, 'sendMessage', { chat_id: to, text: out, disable_web_page_preview: true })
  if (r.ok) { await markAnswered(env, fwid, 'бот', text, by); if (cfg.features.followup) { const fu = await kget(env, 'fu', {}); fu[to] = { at: now() + 86400000, qid: fwid, en: !!q?.en }; await kput(env, 'fu', fu) } }
  return r
}

const ALL_PERMS = { can_send_messages: true, can_send_audios: true, can_send_documents: true, can_send_photos: true, can_send_videos: true, can_send_video_notes: true, can_send_voice_notes: true, can_send_polls: true, can_send_other_messages: true, can_add_web_page_previews: true }
async function groupAdmin(env, chat, uid) { if (String(uid) === String(env.OWNER_ID)) return true; const st = (await tg(env, 'getChatMember', { chat_id: chat, user_id: uid })).result?.status; return st === 'administrator' || st === 'creator' }
async function onGroup(env, cfg, m) { // группа: приветствие, капча, предупреждения и защита от ссылок от новичков
  // /warn и /ban ответом на сообщение — только админы группы; 3 предупреждения — бан на сутки
  const gc = (m.text || '').split(/[\s@]/)[0].toLowerCase()
  if (cfg.features.warns && (gc === '/warn' || gc === '/ban' || gc === '/unwarn') && m.reply_to_message?.from && !m.reply_to_message.from.is_bot) {
    if (!await groupAdmin(env, m.chat.id, m.from.id)) return
    const u = m.reply_to_message.from, name = u.first_name || 'участник', w = await kget(env, 'warn', {}), k = m.chat.id + ':' + u.id
    if (gc === '/unwarn') { delete w[k]; await kput(env, 'warn', w); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: `✅ Предупреждения ${name} сняты` }) }
    w[k] = gc === '/ban' ? 3 : (w[k] || 0) + 1; const ks = Object.keys(w); if (ks.length > 500) delete w[ks[0]]
    if (w[k] >= 3) { delete w[k]; await kput(env, 'warn', w); await tg(env, 'banChatMember', { chat_id: m.chat.id, user_id: u.id, until_date: Math.floor(now() / 1000) + 86400 }); await tg(env, 'deleteMessage', { chat_id: m.chat.id, message_id: m.reply_to_message.message_id }); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: `🚫 ${name} удалён из группы на сутки` }) }
    await kput(env, 'warn', w)
    return tg(env, 'sendMessage', { chat_id: m.chat.id, reply_parameters: { message_id: m.reply_to_message.message_id, allow_sending_without_reply: true }, text: `⚠️ ${name}, предупреждение ${w[k]} из 3. На третьем — бан на сутки.` })
  }
  if (m.new_chat_members && cfg.features.captcha) { // капча: новичок пишет только после «Я не робот», не нажал за 5 минут — удаляем
    const cap = await kget(env, 'capt', [])
    for (const u of m.new_chat_members.filter(u => !u.is_bot).slice(0, 5)) {
      const rr = await tg(env, 'restrictChatMember', { chat_id: m.chat.id, user_id: u.id, permissions: { can_send_messages: false } }); if (!rr.ok) continue
      const r = await tg(env, 'sendMessage', { chat_id: m.chat.id, text: `👋 ${u.first_name || 'Привет'}, нажмите кнопку в течение 5 минут, чтобы писать в группе.`, reply_markup: { inline_keyboard: [[{ text: '✅ Я не робот', callback_data: 'cap:' + u.id }]] } })
      cap.push({ chat: m.chat.id, uid: u.id, mid: r.result?.message_id, at: now() })
    }
    await kput(env, 'capt', cap.slice(-100))
  }
  if (m.new_chat_members && cfg.features.welcome) {
    for (const u of m.new_chat_members.filter(u => !u.is_bot).slice(0, 5)) {
      const j = await kget(env, 'joined', {}); j[u.id] = now(); for (const k in j) if (now() - j[k] > 86400000) delete j[k]; await kput(env, 'joined', j)
      const name = await botName(env)
      await tg(env, 'sendMessage', { chat_id: m.chat.id, text: fill(cfg, cfg.welcome).replaceAll('{имя}', u.first_name || 'друг'), reply_markup: { inline_keyboard: [[{ text: '📥 Как установить', url: name ? `https://t.me/${name}?start=install` : SITE }, { text: '💬 Поддержка', url: cfg.support }]] } })
    }
    return
  }
  if (cfg.features.antispam && !m.from?.is_bot && ((m.entities || []).concat(m.caption_entities || []).some(e => ['url', 'text_link', 'mention'].includes(e.type)) || m.forward_origin)) {
    const j = await kget(env, 'joined', {})
    if (j[m.from.id] && now() - j[m.from.id] < 86400000) {
      await tg(env, 'deleteMessage', { chat_id: m.chat.id, message_id: m.message_id })
      const n = await kget(env, 'spam', 0); await kput(env, 'spam', n + 1)
    }
  }
}

async function todayText(env) {
  const day = new Date().toISOString().slice(0, 10), st = await kget(env, 'st:' + day, {}), q = await kget(env, 'q', [])
  const today = Object.values(st).reduce((a, b) => a + b, 0), cmp = (await kget(env, 'cmp', [])).filter(x => now() - x.at < 86400000), byName = {}
  cmp.forEach(x => { byName[x.name] = (byName[x.name] || 0) + 1 })
  const ids = await kget(env, 'users', []); let fresh = 0
  for (const id of ids.slice(-60)) { const c = await kget(env, 'u:' + id, null); if (c?.first && now() - c.first < 86400000) fresh++ }
  return `📊 Сегодня\n\nВопросов боту: ${today} (без готового ответа: ${st._miss || 0})\nЖдут вашего ответа: ${q.filter(x => !x.ans).length}\nНовых людей за сутки: ${fresh}\nВсего писали боту: ${ids.length}\nЖалобы на серверы: ${Object.entries(byName).map(([n, c]) => n + ' — ' + c).join(', ') || 'нет'}\nСпама удалено в группе: ${await kget(env, 'spam', 0)}`
}
async function topScreen(env, cfg) { // частые вопросы + отвеченные вопросы, из которых можно сделать тему
  const st = await statsText(env, cfg), ans = (await kget(env, 'q', [])).filter(x => x.ansText && x.text).slice(-5).reverse(), kt = await kget(env, 'ktopics', [])
  const text = st + (ans.length ? '\n\n➕ Сделать темой — бот будет сам отвечать так же на похожие вопросы:\n' + ans.map((x, i) => `${i + 1}. «${x.text.slice(0, 50)}» → «${x.ansText.slice(0, 50)}»`).join('\n') : '') + (kt.length ? `\n\n🧩 Темы, созданные в боте: ${kt.length}` : '')
  return { text, rows: [...ans.map((x, i) => [{ text: `➕ ${i + 1}. Сделать темой`, callback_data: 'ad:mt:' + x.id }]), ...(kt.length ? [[{ text: '🧩 Темы из бота', callback_data: 'ad:kt' }]] : [])] }
}

// ── Панель админа: всё кнопками. Видна и работает только у владельца (OWNER_ID)
const ADMIN_KB = { keyboard: [[{ text: '🛠 Админ' }]], resize_keyboard: true, is_persistent: true }
const back = [{ text: '⬅️ Назад', callback_data: 'ad:menu' }]
const ADMIN_SECS = { // «🛠 Админ» по разделам
  rep: ['📊 Сводки и контроль', [[{ text: '📊 Сегодня', callback_data: 'ad:today' }, { text: '🔥 Частые вопросы', callback_data: 'ad:stats' }], [{ text: '🛰 Серверы', callback_data: 'ad:status' }, { text: '🖥 Система и ошибки', callback_data: 'ad:sys' }], [{ text: '🏓 Проверка бота', callback_data: 'ad:ping' }]]],
  ppl: ['👥 Работа с людьми', [[{ text: '📥 Вопросы', callback_data: 'ad:q' }, { text: '👥 Пользователи', callback_data: 'ad:users' }], [{ text: '🚫 Заблокированные', callback_data: 'ad:blocked' }, { text: '🛟 Помощники', callback_data: 'ad:helpers' }]]],
  bc: ['📣 Рассылки и канал', [[{ text: '📣 Рассылка', callback_data: 'ad:bc' }, { text: '📊 Опрос', callback_data: 'ad:poll' }], [{ text: '📊 Итоги опросов', callback_data: 'ad:pres' }, { text: '📢 Пост в канал', callback_data: 'ad:post' }], [{ text: '🎁 Конкурсы', callback_data: 'ad:contests' }]]],
  bot: ['⚙️ Управление ботом', [[{ text: '⚙️ Функции', callback_data: 'ad:feat' }, { text: '💾 Копия данных', callback_data: 'ad:backup' }], [{ text: '🧩 Темы из бота', callback_data: 'ad:kt' }]]] }
const adminKb = () => ({ inline_keyboard: [[{ text: '📥 Вопросы', callback_data: 'ad:q' }, { text: '📊 Сегодня', callback_data: 'ad:today' }], ...Object.entries(ADMIN_SECS).map(([k, v]) => [{ text: v[0], callback_data: 'ad:sec:' + k }])] })
async function adminMenu(env, cfg, chat, edit) {
  const q = (await kget(env, 'q', [])).filter(x => !x.ans).length
  const text = `🛠 Панель админа\n\nЖдут ответа: ${q}\nНажмите кнопку 👇`
  if (edit) return tg(env, 'editMessageText', { chat_id: chat, message_id: edit, text, reply_markup: adminKb() })
  return tg(env, 'sendMessage', { chat_id: chat, text, reply_markup: adminKb() })
}
async function adminScreen(env, cfg, chat, mid, text, rows) { // один экран: текст + кнопки + «Назад», правим то же сообщение
  return tg(env, 'editMessageText', { chat_id: chat, message_id: mid, text: text.slice(0, 4000), disable_web_page_preview: true, reply_markup: { inline_keyboard: [...(rows || []), back] } })
}
async function onAdmin(env, cfg, cq, d) {
  const chat = cq.message.chat.id, mid = cq.message.message_id, [, act, arg] = d.split(':')
  const boss = String(cq.from.id) === String(env.OWNER_ID), wk = boss ? 'await' : 'await:' + cq.from.id, by = boss ? '' : 'помощник ' + (cq.from.username ? '@' + cq.from.username : cq.from.first_name || cq.from.id)
  await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id })
  if (!boss) { if (!HELPER_ACTS.has(act) || !await isHelper(env, cfg, cq.from.id)) return; if (act === 'menu') return helperMenu(env, chat, mid, cq.from.id) }
  if (act === 'hme') { const my = (await kget(env, 'q', [])).filter(x => x.by === by && x.ansAt > now() - 7 * 86400000); return adminScreen(env, cfg, chat, mid, `📈 Ваши ответы за 7 дней: ${my.length}` + (my.length ? '\n\n' + my.slice(-5).reverse().map(x => `• ${x.name}: ${(x.text || '').slice(0, 40)}`).join('\n') : '')) }
  if (act === 'helpers') { // список помощников, кто сколько ответил, добавить и убрать
    const hs = await kget(env, 'helpers', []), stat = await answeredBy(env)
    return adminScreen(env, cfg, chat, mid, `🛟 Помощники (${hs.length} из 5)\nОтвечают на вопросы кнопками; функции, рассылки, баны и данные им недоступны.${cfg.features.helpRR ? '\nНовые вопросы — им по очереди.' : ''}${cfg.features.helpers ? '' : '\n\n⚠️ Помощники выключены — включите в «⚙️ Функции»'}\n\n` + (hs.map(h => '• ' + h.name).join('\n') || 'Пока нет') + '\n\n📈 Кто сколько ответил за 7 дней:\n' + (Object.entries(stat).sort((a, b) => b[1] - a[1]).map(([k, v]) => `${k} — ${v}`).join('\n') || 'ответов не было'),
      [...hs.map(h => [{ text: '🗑 Убрать ' + h.name.slice(0, 24), callback_data: 'ad:hdel:' + h.id }]), ...(hs.length < 5 ? [[{ text: '➕ Добавить помощника', callback_data: 'ad:hadd' }]] : [])])
  }
  if (act === 'hadd') { await kput(env, 'await', { kind: 'hadd' }, 1800); return tg(env, 'sendMessage', { chat_id: chat, text: '🛟 Напишите номер, @ник или имя человека — он должен хотя бы раз написать боту.', reply_markup: { keyboard: [[{ text: '❌ Отмена' }]], resize_keyboard: true } }) }
  if (act === 'hmk') { // сделать помощником (из поиска или карточки)
    const c = await kget(env, 'u:' + arg, null), hs = await kget(env, 'helpers', [])
    if (!hs.some(h => +h.id === +arg) && hs.length < 5) { hs.push({ id: +arg, name: c?.un ? '@' + c.un : (c?.name || String(arg)) }); await kput(env, 'helpers', hs); await tg(env, 'sendMessage', { chat_id: +arg, text: '🛟 Вас назначили помощником поддержки APEX CLIENT. Кнопка «🛟 Помощник» внизу — вопросы людей и ответы кнопками.', reply_markup: HELP_KB }) }
    return onAdmin(env, cfg, { ...cq, id: cq.id + 'x' }, 'ad:helpers')
  }
  if (act === 'hdel') { await kput(env, 'helpers', (await kget(env, 'helpers', [])).filter(h => +h.id !== +arg)); await kput(env, 'await:' + arg, null); await tg(env, 'sendMessage', { chat_id: +arg, text: 'Вы больше не помощник поддержки. Спасибо за помощь! 🙌', reply_markup: { remove_keyboard: true } }); return onAdmin(env, cfg, { ...cq, id: cq.id + 'x' }, 'ad:helpers') }
  if (act === 'menu') return adminMenu(env, cfg, chat, mid)
  if (act === 'sec' && ADMIN_SECS[arg]) return adminScreen(env, cfg, chat, mid, ADMIN_SECS[arg][0] + '\n\nВыберите 👇', ADMIN_SECS[arg][1])
  if (act === 'today') {
    const day = new Date().toISOString().slice(0, 10), st = await kget(env, 'st:' + day, {}), q = await kget(env, 'q', [])
    const today = Object.values(st).reduce((a, b) => a + b, 0), cmp = (await kget(env, 'cmp', [])).filter(x => now() - x.at < 86400000)
    const byName = {}; cmp.forEach(x => { byName[x.name] = (byName[x.name] || 0) + 1 })
    return adminScreen(env, cfg, chat, mid, await todayText(env), [[{ text: '🔄 Обновить', callback_data: 'ad:today' }]])
  }
  if (act === 'q') { // список вопросов без ответа
    const q = (await kget(env, 'q', [])).filter(x => !x.ans).slice(-6).reverse()
    if (!q.length) return adminScreen(env, cfg, chat, mid, '📥 Вопросов без ответа нет 👍')
    return adminScreen(env, cfg, chat, mid, '📥 Без ответа — выберите вопрос:\n\n' + q.map((x, i) => `${i + 1}. ${x.name || 'человек'}: ${(x.text || '(' + (x.kind || 'файл') + ')').slice(0, 80)}`).join('\n'), q.map((x, i) => [{ text: `${i + 1}. ${(x.name || 'человек').slice(0, 20)} — открыть`, callback_data: 'ad:qo:' + x.id }]))
  }
  if (act === 'qo') { // один вопрос: ответить, шаблон, решено, блок
    const x = (await kget(env, 'q', [])).find(y => y.id === +arg); if (!x) return adminScreen(env, cfg, chat, mid, 'Вопрос не найден — возможно, уже закрыт')
    return adminScreen(env, cfg, chat, mid, `❓ ${x.name || 'человек'}${x.en ? ' (EN — ответ переведу)' : ''}\n${new Date(x.at).toLocaleString('ru', { timeZone: 'Europe/Moscow' })} МСК\n\n${x.text || '(' + (x.kind || 'файл') + ' — смотрите пересланное сообщение выше в чате)'}`,
      [[{ text: '✍️ Ответить', callback_data: 'ad:qa:' + x.id }, { text: '⚡ Шаблон', callback_data: 'ad:qt:' + x.id }], [{ text: '✓ Решено', callback_data: 'ad:qd:' + x.id }, ...(boss ? [{ text: '🚫 Заблокировать', callback_data: 'ad:qb:' + x.id }] : [])], [{ text: '⬅️ К списку', callback_data: 'ad:q' }]])
  }
  if (act === 'qa') { await kput(env, wk, { kind: 'reply', qid: +arg }, 1800); return tg(env, 'sendMessage', { chat_id: chat, text: '✍️ Напишите ответ следующим сообщением — бот отправит его человеку.', reply_markup: { keyboard: [[{ text: '❌ Отмена' }]], resize_keyboard: true } }) }
  if (act === 'qt') return adminScreen(env, cfg, chat, mid, '⚡ Выберите шаблон ответа: «1.» — отправить сразу, «✏️» — поправить перед отправкой\n\n' + cfg.quick.map((t, i) => `${i + 1}. ${fill(cfg, t)}`).join('\n'), [...cfg.quick.map((t, i) => [{ text: `${i + 1}. ${t.slice(0, 30)}`, callback_data: `qr:${i}:${arg}` }, { text: '✏️ ' + (i + 1), callback_data: `ad:qe:${i}_${arg}` }]), [{ text: '⬅️ К вопросу', callback_data: 'ad:qo:' + arg }]])
  if (act === 'qe') { // шаблон с правкой: показать текст, владелец копирует, правит и отправляет
    const [i, qid] = String(arg).split('_').map(Number), t = cfg.quick[i]; if (t == null) return adminScreen(env, cfg, chat, mid, 'Шаблон не найден')
    await kput(env, wk, { kind: 'reply', qid }, 1800)
    await tg(env, 'sendMessage', { chat_id: chat, text: '✏️ Скопируйте шаблон ниже, поправьте и отправьте следующим сообщением — бот передаст человеку.', reply_markup: { keyboard: [[{ text: '❌ Отмена' }]], resize_keyboard: true } })
    return tg(env, 'sendMessage', { chat_id: chat, text: fill(cfg, t) })
  }
  if (act === 'qd') { await markAnswered(env, +arg, 'решено без ответа', '', by); return onAdmin(env, cfg, { ...cq, id: cq.id + 'x' }, 'ad:q') }
  if (act === 'qb') {
    const x = (await kget(env, 'q', [])).find(y => y.id === +arg); if (!x) return
    const b = await kget(env, 'block', []); if (!b.includes(+x.uid)) b.push(+x.uid); await kput(env, 'block', b.slice(-5000)); await markAnswered(env, x.id, 'заблокирован')
    return adminScreen(env, cfg, chat, mid, `🚫 ${x.name || 'Человек'} заблокирован: его сообщения больше не приходят. Разблокировать — «🚫 Заблокированные».`, [[{ text: '⬅️ К списку', callback_data: 'ad:q' }]])
  }
  if (act === 'users' || act === 'up') { // список: по 8 на страницу, сначала новые
    const ids = (await kget(env, 'users', [])).slice().reverse(), page = +arg || 0, pg = ids.slice(page * 8, page * 8 + 8), rows = []
    for (const id of pg) { const c = await kget(env, 'u:' + id, null); rows.push([{ text: `👤 ${(c?.name || id).toString().slice(0, 22)}${c?.un ? ' @' + c.un.slice(0, 16) : ''}`, callback_data: 'ad:uc:' + id }]) }
    const nav = []; if (page > 0) nav.push({ text: '◀️', callback_data: 'ad:up:' + (page - 1) }); if ((page + 1) * 8 < ids.length) nav.push({ text: '▶️', callback_data: 'ad:up:' + (page + 1) })
    return adminScreen(env, cfg, chat, mid, `👥 Пользователи бота: ${ids.length}\nЗаблокировано: ${(await kget(env, 'block', [])).length} · без рассылок: ${(await kget(env, 'stop', [])).length}\n\nСтраница ${page + 1} из ${Math.max(1, Math.ceil(ids.length / 8))} — нажмите на человека:`, [[{ text: '🔎 Найти', callback_data: 'ad:uf' }], ...rows, ...(nav.length ? [nav] : [])])
  }
  if (act === 'uc') { const c = await userCard(env, arg), isH = (await kget(env, 'helpers', [])).some(h => +h.id === +arg); return adminScreen(env, cfg, chat, mid, c.text + (isH ? '\n🛟 Помощник поддержки' : ''), c.rows.slice(0, -1).concat(cfg.features.helpers ? [[isH ? { text: '🛟 Убрать из помощников', callback_data: 'ad:hdel:' + arg } : { text: '🛟 Сделать помощником', callback_data: 'ad:hmk:' + arg }]] : [], [[{ text: '⬅️ К пользователям', callback_data: 'ad:users' }]])) }
  if (act === 'uf') { await kput(env, 'await', { kind: 'find' }, 1800); return tg(env, 'sendMessage', { chat_id: chat, text: '🔎 Напишите номер, @ник или имя:', reply_markup: { keyboard: [[{ text: '❌ Отмена' }]], resize_keyboard: true } }) }
  if (act === 'um') { await kput(env, 'await', { kind: 'msg', uid: +arg }, 1800); return tg(env, 'sendMessage', { chat_id: chat, text: '✉️ Напишите сообщение — бот отправит его этому человеку:', reply_markup: { keyboard: [[{ text: '❌ Отмена' }]], resize_keyboard: true } }) }
  if (['ub2', 'uu', 'us', 'ud'].includes(act)) {
    if (act === 'ud' && !arg.endsWith('!')) return adminScreen(env, cfg, chat, mid, '🗑 Удалить данные человека (карточку и вопросы)? Это нельзя отменить.', [[{ text: '🗑 Да, удалить', callback_data: 'ad:ud:' + arg + '!' }, { text: 'Нет', callback_data: 'ad:uc:' + arg }]])
    const id = arg.replace('!', ''); await userAction(env, { ub2: 'ban', uu: 'unban', us: 'stop', ud: 'delete' }[act], id)
    if (act === 'ud') return adminScreen(env, cfg, chat, mid, '🗑 Данные удалены', [[{ text: '⬅️ К пользователям', callback_data: 'ad:users' }]])
    return onAdmin(env, await config(env), { ...cq, id: cq.id + 'x' }, 'ad:uc:' + id)
  }
  if (act === 'blocked') {
    const b = await kget(env, 'block', [])
    return adminScreen(env, cfg, chat, mid, b.length ? `🚫 Заблокировано: ${b.length}. Нажмите, чтобы разблокировать:` : '🚫 Заблокированных нет', b.slice(-8).map(id => [{ text: '✅ Разблокировать ' + id, callback_data: 'ad:ub:' + id }]))
  }
  if (act === 'ub') { await kput(env, 'block', (await kget(env, 'block', [])).filter(x => x !== +arg)); return onAdmin(env, cfg, { ...cq, id: cq.id + 'x' }, 'ad:blocked') }
  if (act === 'feat') return tg(env, 'editMessageText', { chat_id: chat, message_id: mid, text: '⚙️ Функции бота — нажмите, чтобы включить или выключить:', reply_markup: settingsKb(cfg) }) // разделы; «⬅️ Разделы» внутри раздела вернёт сюда
  if (act === 'status') return adminScreen(env, cfg, chat, mid, await statusText(), [[{ text: '🔄 Обновить', callback_data: 'ad:status' }]])
  if (act === 'stats') { const t = await topScreen(env, cfg); return adminScreen(env, cfg, chat, mid, t.text, t.rows) }
  if (act === 'mt') { // сделать тему из отвеченного вопроса: слова — из вопроса, ответ — ваш
    const x = (await kget(env, 'q', [])).find(y => y.id === +arg); if (!x?.ansText) return adminScreen(env, cfg, chat, mid, 'Вопрос не найден')
    const words = [...new Set(x.text.toLowerCase().replace(/[^\p{L}\s]/gu, ' ').split(/\s+/).filter(w => w.length > 4).map(w => w.slice(0, 7)))].slice(0, 5)
    const kt = await kget(env, 'ktopics', []); kt.push({ id: 'k' + now().toString(36), title: '💬 ' + x.text.slice(0, 30), words, text: x.ansText }); await kput(env, 'ktopics', kt.slice(-12)); cfgCache = null
    return adminScreen(env, cfg, chat, mid, `✅ Тема добавлена: «${x.text.slice(0, 40)}»\nСлова для поиска: ${words.join(', ') || '—'}\nБот будет отвечать вашим ответом.`, [[{ text: '🧩 Темы из бота', callback_data: 'ad:kt' }]])
  }
  if (act === 'kt') { const kt = await kget(env, 'ktopics', []); return adminScreen(env, cfg, chat, mid, kt.length ? '🧩 Темы, созданные в боте (нажмите, чтобы удалить):\n\n' + kt.map((t, i) => `${i + 1}. ${t.title} — ${t.words.join(', ')}`).join('\n') : '🧩 Тем из бота нет', kt.map(t => [{ text: '🗑 ' + t.title.slice(0, 30), callback_data: 'ad:ktd:' + t.id }])) }
  if (act === 'ktd') { await kput(env, 'ktopics', (await kget(env, 'ktopics', [])).filter(t => t.id !== arg)); cfgCache = null; return onAdmin(env, await config(env), { ...cq, id: cq.id + 'x' }, 'ad:kt') }
  if (act === 'ping') { const t0 = now(); return adminScreen(env, cfg, chat, mid, `🏓 Бот работает · ${env.HOST_INFO || 'Cloudflare'}\nПодписчиков бота: ${(await kget(env, 'users', [])).length}\nФункций включено: ${Object.values(cfg.features).filter(Boolean).length} из ${Object.keys(cfg.features).length}\nОтвет за ${now() - t0} мс`) }
  if (act === 'contests') {
    if (!env.APEX_KV) return adminScreen(env, cfg, chat, mid, 'Конкурсы выключены: нет хранилища')
    const list = (await env.APEX_KV.list({ prefix: 'c:' })).keys.slice(-8), rows = [], lines = []
    for (const k of list) { const c = JSON.parse(await env.APEX_KV.get(k.name) || '{}'), cid = k.name.slice(2); lines.push(`${cid} — ${(c.ids || []).length} уч.${c.closed ? ', итоги подведены' : ''}`); if (!c.closed) rows.push([{ text: '🏆 Итоги ' + cid, callback_data: 'ad:draw:' + cid }]) }
    const auto = await kget(env, 'cauto', [])
    return adminScreen(env, cfg, chat, mid, '🎁 Конкурсы\n\n' + (lines.join('\n') || 'Пока нет — создайте здесь или в пульте: Telegram → Конкурс') + (auto.length ? '\n\n⏰ Итоги подведу сам:\n' + auto.map(a => `${a.cid} — ${mskTime(a.at)} МСК, победителей ${a.n}`).join('\n') : ''), [...(cfg.features.contestBot ? [[{ text: '➕ Новый конкурс', callback_data: 'ad:cnew' }]] : []), ...rows])
  }
  if (act === 'cnew') { // конкурс из бота: текст поста → срок → пост в канале с кнопкой «🎉 Участвую»
    if (!cfg.features.contestBot) return adminScreen(env, cfg, chat, mid, '🎁 Конкурс из бота выключен — включите в «⚙️ Функции»')
    if (!cfg.channel) return adminScreen(env, cfg, chat, mid, '⚠️ Не указан канал — впишите его в пульте: Telegram → Бот → Канал')
    await kput(env, 'await', { kind: 'contest' }, 1800)
    return tg(env, 'sendMessage', { chat_id: chat, text: `🎁 Новый конкурс в ${cfg.channel}\nНапишите текст поста: что разыгрываем и условия. Кнопку «🎉 Участвую» и дату итогов бот добавит сам.`, reply_markup: { keyboard: [[{ text: '❌ Отмена' }]], resize_keyboard: true } })
  }
  if (act === 'cgo') {
    const d = await kget(env, 'cdraft', null); if (!d) return adminScreen(env, cfg, chat, mid, 'Черновик конкурса устарел — начните заново')
    const [days, n] = String(arg).split('_').map(Number), cid = 'b' + now().toString(36), at = now() + Math.min(Math.max(days, 1), 30) * 86400000
    const r = await tg(env, 'sendMessage', { chat_id: cfg.channel, text: `${d}\n\n🏆 Итоги: ${mskTime(at)} МСК`, disable_web_page_preview: true, reply_markup: { inline_keyboard: [[{ text: '🎉 Участвую', callback_data: 'join:' + cid }]] } })
    if (!r.ok) return adminScreen(env, cfg, chat, mid, '⚠️ Канал не принял пост: ' + (r.description || 'ошибка') + '\nБот должен быть админом канала с правом «Публиковать».')
    await kput(env, 'cdraft', null); const auto = await kget(env, 'cauto', []); auto.push({ cid, at, n: Math.min(Math.max(n, 1), 10), mid: r.result?.message_id }); await kput(env, 'cauto', auto.slice(-10))
    return adminScreen(env, cfg, chat, mid, `✅ Конкурс ${cid} опубликован. Итоги подведу сам ${mskTime(at)} МСК и напишу в канал и вам.`, [[{ text: '⬅️ К конкурсам', callback_data: 'ad:contests' }]])
  }
  if (act === 'draw') { const r = await draw(env, arg, 1); return adminScreen(env, cfg, chat, mid, r.error ? '⚠️ ' + r.error : `🏆 Конкурс ${arg}: участников ${r.count}\nПобедитель: ${r.winners.map(who).join(', ') || 'нет'}`, [[{ text: '⬅️ К конкурсам', callback_data: 'ad:contests' }]]) }
  if (act === 'bc') { await kput(env, 'await', { kind: 'bc' }, 1800); return tg(env, 'sendMessage', { chat_id: chat, text: `📣 Рассылка всем, кто писал боту (${(await kget(env, 'users', [])).length} чел.).\nНапишите текст следующим сообщением — я покажу, как он выглядит, и спрошу подтверждение.`, reply_markup: { keyboard: [[{ text: '❌ Отмена' }]], resize_keyboard: true } }) }
  if (act === 'bcok') {
    const draft = await kget(env, 'bcdraft', null); if (!draft) return adminScreen(env, cfg, chat, mid, 'Черновик рассылки устарел — начните заново')
    await kput(env, 'bcadm', { id: 'a' + now().toString(36), text: draft }); await kput(env, 'bcdraft', null)
    return adminScreen(env, cfg, chat, mid, '✅ Рассылка запущена — бот отправляет по 25 человек в минуту.')
  }
  if (act === 'bcno') { for (const k of ['bcdraft', 'polldraft', 'postdraft', 'cdraft']) await kput(env, k, null); return adminMenu(env, cfg, chat, mid) }
  if (act === 'post') { // пост в канал из бота
    if (!cfg.features.chpost) return adminScreen(env, cfg, chat, mid, '📢 Пост в канал выключен — включите в «⚙️ Функции»')
    if (!cfg.channel) return adminScreen(env, cfg, chat, mid, '⚠️ Не указан канал — впишите его в пульте: Telegram → Бот → Канал')
    await kput(env, 'await', { kind: 'post' }, 1800)
    return tg(env, 'sendMessage', { chat_id: chat, text: `📢 Пост в ${cfg.channel}\nНапишите текст следующим сообщением — покажу, как он выглядит, и спрошу подтверждение.`, reply_markup: { keyboard: [[{ text: '❌ Отмена' }]], resize_keyboard: true } })
  }
  if (act === 'postok') {
    const d = await kget(env, 'postdraft', null); if (!d) return adminScreen(env, cfg, chat, mid, 'Черновик поста устарел — начните заново')
    const r = await tg(env, 'sendMessage', { chat_id: cfg.channel, text: d, disable_web_page_preview: false }); if (r.ok) await kput(env, 'postdraft', null)
    return adminScreen(env, cfg, chat, mid, r.ok ? '✅ Опубликовано в ' + cfg.channel : '⚠️ Канал не принял пост: ' + (r.description || 'ошибка') + '\nБот должен быть админом канала с правом «Публиковать».')
  }
  if (act === 'backup') { // копия всех данных бота файлом — только вам в личку
    if (!cfg.features.backup) return adminScreen(env, cfg, chat, mid, '💾 Копия данных выключена — включите в «⚙️ Функции»')
    const r = await tgFile(env, chat, `apex-bot-${new Date().toISOString().slice(0, 10)}.json`, JSON.stringify(await dataExport(env), null, 1), '💾 Копия данных бота: вопросы, пользователи, статистика, опросы. Храните у себя, никому не пересылайте.')
    return adminScreen(env, cfg, chat, mid, r.ok ? '💾 Файл с копией отправлен ниже 👇' : '⚠️ Не отправилось: ' + (r.description || 'ошибка'))
  }
  if (act === 'sys') { // система: где работает, сколько, последние ошибки; перезапуск — только на своём сервере
    if (!cfg.features.syslog) return adminScreen(env, cfg, chat, mid, '🖥 Система и ошибки выключены — включите в «⚙️ Функции»')
    const errs = (await errList(env)).slice(-8).reverse(), up = env.STARTED ? Math.round((now() - env.STARTED) / 60000) : null
    const text = `🖥 Система\n\nГде работает: ${env.HOST_INFO || 'Cloudflare'}${up != null ? `\nРаботает без перезапуска: ${up >= 1440 ? Math.floor(up / 1440) + ' д ' : ''}${Math.floor(up % 1440 / 60)} ч ${up % 60} мин` : ''}\n\n` + (errs.length ? '⚠️ Последние ошибки:\n' + errs.map(e => `${mskTime(e.at)} — ${e.where}: ${e.msg}`).join('\n') : '✅ Ошибок нет')
    return adminScreen(env, cfg, chat, mid, text, [[{ text: '🔄 Обновить', callback_data: 'ad:sys' }, ...(errs.length ? [{ text: '🧹 Очистить', callback_data: 'ad:sysclr' }] : [])], ...(env.RESTART ? [[{ text: '♻️ Перезапустить бота', callback_data: 'ad:rst' }]] : [])])
  }
  if (act === 'sysclr') { if (Array.isArray(env.ERRLOG)) env.ERRLOG.length = 0; else await kput(env, 'errlog', []); return onAdmin(env, cfg, { ...cq, id: cq.id + 'x' }, 'ad:sys') }
  if (act === 'rst') {
    if (!env.RESTART || !cfg.features.syslog) return adminScreen(env, cfg, chat, mid, 'Перезапуск есть только на своём сервере')
    if (arg !== 'ok') return adminScreen(env, cfg, chat, mid, '♻️ Перезапустить бота? Он вернётся через несколько секунд.', [[{ text: '♻️ Да, перезапустить', callback_data: 'ad:rst:ok' }]])
    await adminScreen(env, cfg, chat, mid, '♻️ Перезапускаю… Через несколько секунд нажмите «🛠 Админ».'); env.RESTART(); return
  }
  if (act === 'poll') { // опрос из бота: вопрос и варианты одним сообщением
    if (!cfg.features.polls) return adminScreen(env, cfg, chat, mid, '📊 Опросы выключены — включите в «⚙️ Функции»')
    await kput(env, 'await', { kind: 'poll' }, 1800)
    return tg(env, 'sendMessage', { chat_id: chat, text: `📊 Опрос всем, кто писал боту (${(await kget(env, 'users', [])).length} чел.).\nНапишите одним сообщением: первая строка — вопрос, следующие — варианты (от 2 до 10). Например:\n\nКак работает приложение?\nОтлично\nИногда тормозит\nНе подключается`, reply_markup: { keyboard: [[{ text: '❌ Отмена' }]], resize_keyboard: true } })
  }
  if (act === 'pok') {
    const p = await kget(env, 'polldraft', null); if (!p) return adminScreen(env, cfg, chat, mid, 'Черновик опроса устарел — начните заново')
    await kput(env, 'polladm', { id: 'p' + now().toString(36), poll: p }); await kput(env, 'polldraft', null)
    return adminScreen(env, cfg, chat, mid, '✅ Опрос запущен — бот отправляет по 25 человек в минуту. Итоги — «📊 Итоги опросов».', [[{ text: '📊 Итоги опросов', callback_data: 'ad:pres' }]])
  }
  if (act === 'pres') { // итоги: голоса из всех копий опроса складываем по вопросу
    const by = {}
    for (const r of Object.values(await kget(env, 'pollres', {}))) { const x = by[r.q] ||= { at: 0, o: {} }; x.at = Math.max(x.at, r.at || 0); for (const [t, c] of r.o) x.o[t] = (x.o[t] || 0) + c }
    const list = Object.entries(by).sort((a, b) => b[1].at - a[1].at).slice(0, 5)
    return adminScreen(env, cfg, chat, mid, list.length ? '📊 Итоги опросов\n\n' + list.map(([q, x]) => { const sum = Object.values(x.o).reduce((a, b) => a + b, 0) || 1; return `❓ ${q}\n` + Object.entries(x.o).map(([t, c]) => `  ${t} — ${c} (${Math.round(c * 100 / sum)}%)`).join('\n') }).join('\n\n') : '📊 Голосов пока нет', [[{ text: '🔄 Обновить', callback_data: 'ad:pres' }]])
  }
}
async function adminInput(env, cfg, chat, wait, text, helper) { // ждали текст: ответ человеку (владелец или помощник), рассылку и т. д. (только владелец)
  await kput(env, helper ? 'await:' + helper.id : 'await', null)
  if (wait.kind === 'reply') {
    const kb = helper ? HELP_KB : ADMIN_KB, x = (await kget(env, 'q', [])).find(y => y.id === wait.qid); if (!x) return tg(env, 'sendMessage', { chat_id: chat, text: 'Вопрос не найден', reply_markup: kb })
    if (helper && x.ans) return tg(env, 'sendMessage', { chat_id: chat, text: '👌 На этот вопрос уже ответили (' + (x.by || 'владелец') + ')', reply_markup: kb })
    const r = await sendToUser(env, cfg, x.uid, text, x.id, helper ? 'помощник ' + helper.name : '')
    if (r.ok && helper && env.OWNER_ID) await tg(env, 'sendMessage', { chat_id: env.OWNER_ID, text: `🛟 Помощник ${helper.name} ответил ${x.name}:\n${text.slice(0, 500)}` })
    return tg(env, 'sendMessage', { chat_id: chat, text: r.ok ? '✅ Ответ отправлен ' + (x.name || '') : '⚠️ Не отправилось: ' + (r.description || 'ошибка'), reply_markup: kb })
  }
  if (helper) return
  if (wait.kind === 'hadd') {
    const q = text.trim().replace(/^@/, '').toLowerCase(), hits = (await userList(env, 500)).filter(c => String(c.id) === q || (c.un || '').toLowerCase() === q || (c.name || '').toLowerCase().includes(q)).slice(0, 6)
    return tg(env, 'sendMessage', { chat_id: chat, text: hits.length ? '🛟 Кого сделать помощником?' : '🔎 Никого не нашлось — человек должен сначала написать боту', reply_markup: hits.length ? { inline_keyboard: hits.map(c => [{ text: `🛟 ${(c.name || c.id).toString().slice(0, 22)}${c.un ? ' @' + c.un : ''}`, callback_data: 'ad:hmk:' + c.id }]) } : ADMIN_KB })
  }
  if (wait.kind === 'find') {
    const q = text.trim().replace(/^@/, '').toLowerCase(), list = await userList(env, 500)
    const hits = list.filter(c => String(c.id) === q || (c.un || '').toLowerCase() === q || (c.name || '').toLowerCase().includes(q)).slice(0, 8)
    return tg(env, 'sendMessage', { chat_id: chat, text: hits.length ? `🔎 Нашлось: ${hits.length}` : '🔎 Никого не нашлось', reply_markup: hits.length ? { inline_keyboard: hits.map(c => [{ text: `👤 ${(c.name || c.id).toString().slice(0, 22)}${c.un ? ' @' + c.un : ''}`, callback_data: 'ad:uc:' + c.id }]) } : ADMIN_KB })
  }
  if (wait.kind === 'msg') { const r = await userAction(env, 'msg', wait.uid, text); return tg(env, 'sendMessage', { chat_id: chat, text: r.ok ? '✅ Отправлено' : '⚠️ ' + r.error, reply_markup: ADMIN_KB }) }
  if (wait.kind === 'post' || wait.kind === 'contest') {
    const t = text.slice(0, 3500); await kput(env, wait.kind === 'post' ? 'postdraft' : 'cdraft', t, 3600)
    await tg(env, 'sendMessage', { chat_id: chat, text: 'Так увидят в канале 👇', reply_markup: ADMIN_KB })
    if (wait.kind === 'post') return tg(env, 'sendMessage', { chat_id: chat, text: t, reply_markup: { inline_keyboard: [[{ text: '✅ Опубликовать', callback_data: 'ad:postok' }, { text: '❌ Отмена', callback_data: 'ad:bcno' }]] } })
    return tg(env, 'sendMessage', { chat_id: chat, text: t + '\n\n🏆 Итоги: …\n\nКогда подвести итоги?', reply_markup: { inline_keyboard: [[{ text: '1 день', callback_data: 'ad:cgo:1_1' }, { text: '3 дня', callback_data: 'ad:cgo:3_1' }, { text: '7 дней', callback_data: 'ad:cgo:7_1' }], [{ text: '7 дней, 3 победителя', callback_data: 'ad:cgo:7_3' }], [{ text: '❌ Отмена', callback_data: 'ad:bcno' }]] } })
  }
  if (wait.kind === 'poll') {
    const [q, ...opts] = text.split('\n').map(x => x.trim()).filter(Boolean), options = [...new Set(opts)].slice(0, 10)
    if (!q || options.length < 2) return tg(env, 'sendMessage', { chat_id: chat, text: '⚠️ Нужно: первая строка — вопрос, дальше хотя бы 2 варианта. Нажмите «📊 Опрос» ещё раз.', reply_markup: ADMIN_KB })
    const p = { q: q.slice(0, 300), options: options.map(o => o.slice(0, 100)) }; await kput(env, 'polldraft', p, 3600)
    await tg(env, 'sendMessage', { chat_id: chat, text: 'Так увидят люди 👇', reply_markup: ADMIN_KB })
    await tg(env, 'sendPoll', { chat_id: chat, question: p.q, options: p.options.map(o => ({ text: o })), is_anonymous: true })
    return tg(env, 'sendMessage', { chat_id: chat, text: `Отправить опрос всем (${(await kget(env, 'users', [])).length} чел.)?`, reply_markup: { inline_keyboard: [[{ text: '✅ Отправить всем', callback_data: 'ad:pok' }, { text: '❌ Отмена', callback_data: 'ad:bcno' }]] } })
  }
  if (wait.kind === 'bc') {
    await kput(env, 'bcdraft', text.slice(0, 3500), 3600)
    await tg(env, 'sendMessage', { chat_id: chat, text: 'Так увидят люди 👇', reply_markup: ADMIN_KB })
    return tg(env, 'sendMessage', { chat_id: chat, text: fill(cfg, text).slice(0, 3500) + '\n\n/stop — не получать рассылки', disable_web_page_preview: true, reply_markup: { inline_keyboard: [[{ text: '✅ Отправить всем', callback_data: 'ad:bcok' }, { text: '❌ Отмена', callback_data: 'ad:bcno' }]] } })
  }
}

async function onMessage(env, m) {
  const cfg = await config(env)
  if (m.chat?.type === 'group' || m.chat?.type === 'supergroup') return onGroup(env, cfg, m)
  if (m.chat?.type !== 'private') return // в каналах молчим
  const text = (m.text || '').trim(), owner = env.OWNER_ID && String(m.from?.id) === String(env.OWNER_ID), en = isEn(cfg, m.from)
  const [cmd0, arg] = text.split(/\s+/, 2), cmd = (cmd0 || '').split('@')[0].toLowerCase()
  if (!owner && (await kget(env, 'block', [])).includes(m.from.id)) return // заблокирован админом — молчим
  if (!owner && cfg.features.antiflood) { const f = flooded(m.from.id); if (f === 'warn') return tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? 'Too many messages — please wait a minute 🙏' : 'Слишком часто — подождите минуту 🙏' }); if (f) return }
  if (!owner && env.APEX_KV) await remember(env, m.from)
  // ── Помощник: только вопросы и ответы
  if (!owner && await isHelper(env, cfg, m.from?.id)) {
    const hw = 'await:' + m.from.id, h = { id: m.from.id, name: m.from.username ? '@' + m.from.username : (m.from.first_name || String(m.from.id)) }
    if (text === '🛟 Помощник' || cmd === '/helper') return helperMenu(env, m.chat.id, 0, m.from.id)
    const wait = await kget(env, hw, null)
    if (wait && (text === '❌ Отмена' || text === '/cancel')) { await kput(env, hw, null); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: 'Отменено', reply_markup: HELP_KB }) }
    if (wait && text && !text.startsWith('/')) return adminInput(env, cfg, m.chat.id, wait, text, h)
  }
  // ── Панель админа на кнопках (только владелец)
  if (owner && cfg.features.admin) {
    if (text === '🛠 Админ' || cmd === '/admin') return adminMenu(env, cfg, m.chat.id)
    const wait = await kget(env, 'await', null)
    if (wait && text && !m.reply_to_message && !text.startsWith('/') && text !== '❌ Отмена') return adminInput(env, cfg, m.chat.id, wait, text)
    if (wait && (text === '❌ Отмена' || text === '/cancel')) { await kput(env, 'await', null); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: 'Отменено', reply_markup: ADMIN_KB }) }
  }
  // Владелец ответил на пересланный вопрос — отправляем ответ человеку
  if (owner && m.reply_to_message && env.APEX_KV) {
    const to = await env.APEX_KV.get('fw:' + m.reply_to_message.message_id)
    if (to) {
      const r = m.text ? await sendToUser(env, cfg, to, m.text, m.reply_to_message.message_id) : await tg(env, 'copyMessage', { chat_id: to, from_chat_id: m.chat.id, message_id: m.message_id })
      if (!m.text && r.ok) await markAnswered(env, m.reply_to_message.message_id, 'бот')
      return tg(env, 'sendMessage', { chat_id: m.chat.id, text: r.ok ? '✅ Ответ отправлен' : '⚠️ Не отправилось: ' + (r.description || 'ошибка') })
    }
  }
  if (owner && cmd === '/ping' && cfg.features.ping) {
    const t0 = now(); await config(env)
    return tg(env, 'sendMessage', { chat_id: m.chat.id, text: `🏓 Работаю · ${env.HOST_INFO || 'Cloudflare'}\nПодписчиков бота: ${(await kget(env, 'users', [])).length}\nФункции: ${Object.entries(cfg.features).map(([k, v]) => (v ? '✅' : '▫️') + k).join(' ')}\nОтвет за ${now() - t0} мс` })
  }
  if (owner && (cmd === '/settings' || cmd === '/features')) return tg(env, 'sendMessage', { chat_id: m.chat.id, text: '⚙️ Функции бота — нажмите, чтобы включить или выключить. Действует сразу, пульт и меню сервера это видят.', reply_markup: settingsKb(cfg) })
  if (owner && cmd === '/user' && /^\d+$/.test(arg || '')) { const c = await userCard(env, arg); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: c.text, reply_markup: { inline_keyboard: c.rows } }) }
  if (owner && cmd === '/today') return tg(env, 'sendMessage', { chat_id: m.chat.id, text: await todayText(env), reply_markup: { inline_keyboard: [[{ text: '📥 Вопросы', callback_data: 'ad:q' }, { text: '🛠 Админ', callback_data: 'ad:menu' }]] } })
  if (owner && cmd === '/queue') { const q = (await kget(env, 'q', [])).filter(x => !x.ans).slice(-8).reverse(); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: q.length ? `📥 Без ответа: ${q.length}` : '📥 Вопросов без ответа нет 👍', reply_markup: { inline_keyboard: q.map((x, i) => [{ text: `${i + 1}. ${(x.name || 'человек').slice(0, 16)}: ${(x.text || x.kind || '').slice(0, 24)}`, callback_data: 'ad:qo:' + x.id }]) } }) }
  if (owner && cmd === '/top') { const t = await topScreen(env, cfg); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: t.text, reply_markup: { inline_keyboard: t.rows } }) }
  if (owner && (cmd === '/users')) return tg(env, 'sendMessage', { chat_id: m.chat.id, text: '👥 Пользователи', reply_markup: { inline_keyboard: [[{ text: 'Открыть список', callback_data: 'ad:users' }]] } })
  if (owner && cmd === '/stats') return tg(env, 'sendMessage', { chat_id: m.chat.id, text: await statsText(env, cfg) })
  if (owner && cmd === '/help') return tg(env, 'sendMessage', { chat_id: m.chat.id, text: 'Команды владельца:\n/settings — включить и выключить функции бота\n/ping — работает ли бот\n/stats — какие темы спрашивают за неделю\n/status — состояние серверов\n/news — последние новости\n/contests — конкурсы\n/draw <номер> [победителей] — подвести итоги\nОтветьте на пересланный вопрос (или нажмите ⚡ быстрый ответ) — бот отправит ответ человеку.' })
  if (cmd === '/status' && cfg.features.status) return tg(env, 'sendMessage', { chat_id: m.chat.id, text: await statusText(), disable_web_page_preview: true })
  if (cmd === '/quiz' && cfg.features.quiz) { const q = quizMsg(0, 0); return tg(env, 'sendMessage', { chat_id: m.chat.id, ...q }) }
  if (cmd === '/report' && cfg.features.form && !owner) { await kput(env, 'pf:' + m.from.id, { step: 0, a: [] }, 1800); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: '📝 Сообщить о проблеме — 3 коротких вопроса.\n\n1/3. Какое у вас устройство? (например: Samsung A52, Android 13 или телевизор Xiaomi)', reply_markup: { keyboard: [[{ text: '❌ Отмена' }]], resize_keyboard: true, one_time_keyboard: true } }) }
  if (cmd === '/download' || cmd === '/app' || cmd === '/version') { const v = await latestVersion(); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: (en ? 'APEX CLIENT ' : 'APEX CLIENT ') + (v || '') + (en ? '\nThe file is too big for Telegram (70 MB) — download it from the site:' : '\nФайл больше, чем пропускает Telegram (70 МБ), — скачайте с сайта:'), reply_markup: { inline_keyboard: [[{ text: en ? '⬇️ Download' : '⬇️ Скачать с сайта', url: SITE }], [{ text: en ? 'Mirror (GitHub)' : 'Запасная ссылка (GitHub)', url: 'https://github.com/Dubstep2004/apex-client/releases/latest' }]] } }) }
  if (cmd === '/news' && cfg.features.news) return tg(env, 'sendMessage', { chat_id: m.chat.id, text: await newsText(cfg), disable_web_page_preview: true })
  if (cmd === '/stop') { const st = await kget(env, 'stop', []); if (!st.includes(m.from.id)) st.push(m.from.id); await kput(env, 'stop', st.slice(-50000)); await kput(env, 'users', (await kget(env, 'users', [])).filter(x => x !== m.from.id)); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: 'Хорошо, рассылок больше не будет. Вернуть — /start' }) }
  if (cmd === '/remind' && cfg.features.remind) {
    const r = await kget(env, 'rmd', []), on = !r.includes(m.from.id)
    await kput(env, 'rmd', on ? [...r, m.from.id].slice(-50000) : r.filter(x => x !== m.from.id))
    return tg(env, 'sendMessage', { chat_id: m.chat.id, text: on ? '🔔 Напомню за 3 дня до конца подписки (если ваш Telegram привязан к подписке). Выключить — /remind' : '🔕 Напоминания выключены' })
  }
  if (cmd === '/invite' && cfg.features.invite) {
    const name = await botName(env), refc = await kget(env, 'refc', {})
    return tg(env, 'sendMessage', { chat_id: m.chat.id, text: `🤝 Ваша ссылка для друзей:\nhttps://t.me/${name}?start=ref_${m.from.id}\n\nПриглашено: ${refc[m.from.id] || 0}` })
  }
  if (cmd === '/problem' && cfg.features.complaints) return tg(env, 'sendMessage', { chat_id: m.chat.id, text: 'Напишите, какой сервер не работает — например «не работает Германия». Если таких сообщений будет несколько, поддержка сразу узнает.' })
  if (owner && text.startsWith('/draw')) {
    const [, cid, n] = text.split(/\s+/)
    const r = await draw(env, cid, +n || 1)
    return tg(env, 'sendMessage', { chat_id: m.chat.id, text: r.error ? '⚠️ ' + r.error : `Конкурс ${cid}: участников ${r.count}. Победители: ${r.winners.map(who).join(', ') || 'нет'}` })
  }
  if (owner && text === '/contests') {
    if (!env.APEX_KV) return tg(env, 'sendMessage', { chat_id: m.chat.id, text: 'Нет привязки APEX_KV — конкурсы выключены' })
    const list = await env.APEX_KV.list({ prefix: 'c:' })
    const rows = await Promise.all(list.keys.slice(0, 20).map(async k => { const c = JSON.parse(await env.APEX_KV.get(k.name) || '{}'); return `${k.name.slice(2)} — ${(c.ids || []).length} уч.${c.closed ? ', завершён' : ''}` }))
    return tg(env, 'sendMessage', { chat_id: m.chat.id, text: rows.join('\n') || 'Конкурсов нет' })
  }
  if (cmd === '/start') {
    // Ссылки t.me/бот?start=тема и приглашения ref_<номер>
    if (arg && /^ref_\d+$/.test(arg) && cfg.features.invite && String(m.from.id) !== arg.slice(4)) {
      const k = 'ref:' + m.from.id
      if (!(await kget(env, k, null))) { await kput(env, k, arg.slice(4)); const rc = await kget(env, 'refc', {}); rc[arg.slice(4)] = (rc[arg.slice(4)] || 0) + 1; await kput(env, 'refc', rc) }
    }
    if (owner && cfg.features.admin && !arg) return tg(env, 'sendMessage', { chat_id: m.chat.id, text: '👋 Вы владелец бота. Кнопка «🛠 Админ» внизу — панель управления. Её видите только вы.', reply_markup: ADMIN_KB })
    const f = arg && cfg.features.deeplinks && topicsOf(cfg, en).find(t => t.id === arg)
    if (f) return answer(env, cfg, m.chat.id, f, en)
    return tg(env, 'sendMessage', { chat_id: m.chat.id, text: (en ? EN.greeting : fill(cfg, cfg.greeting)) + await fakeWarn(env, cfg, en), reply_markup: menu(cfg, en) })
  }
  if (cmd === '/help' || (!text && !m.photo && !m.document && !m.voice && !m.video && !m.video_note)) {
    return tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? EN.greeting : fill(cfg, cfg.greeting), reply_markup: menu(cfg, en) })
  }
  // Незнакомая или выключенная команда — не пересылаем владельцу, показываем темы
  if (text.startsWith('/')) return tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? EN.pick : 'Такой команды нет 🙂 Выберите тему:', reply_markup: menu(cfg, en) })
  // Фото, голосовые, файлы — владельцу
  const kind = m.photo ? 'скриншот' : m.voice || m.video_note ? 'голосовое' : m.document ? 'файл' : m.video ? 'видео' : ''
  if (kind && !(cfg.features.form && env.APEX_KV && await kget(env, 'pf:' + m.from.id, null))) { if (cfg.features.media && await forwardToOwner(env, cfg, m, en, kind)) return; return tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? EN.notFound : fill(cfg, cfg.notFound), reply_markup: after(cfg, en) }) }
  // Форма /report: устройство → версия → что не так; итог одной карточкой в поддержку
  if (!owner && cfg.features.form && (text || kind) && env.APEX_KV) {
    const pf = await kget(env, 'pf:' + m.from.id, null)
    if (pf) {
      if (text === '❌ Отмена') { await kput(env, 'pf:' + m.from.id, null); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: 'Отменено 👌', reply_markup: { remove_keyboard: true } }) }
      pf.a.push(text || '(' + kind + ')'); pf.step++
      if (pf.step === 1) { await kput(env, 'pf:' + m.from.id, pf, 1800); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: '2/3. Какая версия приложения? (Настройки → внизу) Если не знаете — напишите «не знаю».' }) }
      if (pf.step === 2) { await kput(env, 'pf:' + m.from.id, pf, 1800); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: '3/3. Что не так? Опишите одним сообщением или пришлите скриншот.' }) }
      await kput(env, 'pf:' + m.from.id, null)
      if (await forwardToOwner(env, cfg, m, en, kind || 'text')) { if (env.OWNER_ID) await tg(env, 'sendMessage', { chat_id: env.OWNER_ID, text: `📝 Форма от ${who({ username: m.from.username, name: m.from.first_name })}\nУстройство: ${pf.a[0]}\nВерсия: ${pf.a[1]}\nПроблема: ${pf.a[2]}`.slice(0, 1500) }); return }
      return tg(env, 'sendMessage', { chat_id: m.chat.id, text: 'Спасибо! Напишите это же в поддержку: ' + cfg.support, reply_markup: { remove_keyboard: true } })
    }
  }
  // Память разговора: «не помогло» сразу после темы — передаём людям и говорим, что человек уже читал
  if (!owner && text && cfg.features.memory && /^(не помогл|не получ|всё равно не|все равно не|не работает|didn'?t help|still not)/i.test(text)) {
    const lt = lastTopic.get(String(m.chat.id))
    if (lt && now() - lt.at < 3600000 && await forwardToOwner(env, cfg, m, en, 'text')) { lastTopic.delete(String(m.chat.id)); if (env.OWNER_ID) await tg(env, 'sendMessage', { chat_id: env.OWNER_ID, text: `🧠 Перед этим человек читал тему «${lt.t}» — она не помогла` }); return }
  }
  // «🙋 Позвать человека»: следующее сообщение — сразу людям, без поиска по темам
  if (!owner && text && cfg.features.human && await kget(env, 'hw:' + m.from.id, null)) { await kput(env, 'hw:' + m.from.id, null); if (await forwardToOwner(env, cfg, m, en, 'text')) return }
  // «у меня 3.20» — подскажем новую версию
  const vm = cfg.features.version && text.match(/(?:^|\s)v?(\d+\.\d+(?:\.\d+)?)(?:\s|$|[,.!?])/)
  if (vm) {
    const last = await latestVersion()
    if (last && vcmp(vm[1], last) < 0) return tg(env, 'sendMessage', { chat_id: m.chat.id, text: (en ? `There is a newer version: ${last}. Update in the app («Update» banner) or download:` : `Есть версия новее: ${last}. Обновите в приложении (плашка «Вышла версия») или скачайте:`) + `\n${SITE}`, reply_markup: { inline_keyboard: [[{ text: en ? '⬇️ Download' : '⬇️ Скачать', url: SITE }, { text: en ? 'How to update' : 'Как обновить', url: SITE + '/update/' }]] } })
    if (last && vcmp(vm[1], last) >= 0 && /верси|version|обнов/i.test(text)) return tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? `✅ ${vm[1]} is the latest version` : `✅ ${vm[1]} — последняя версия` })
  }
  // Жалоба на сервер: «не работает Германия»
  const cm = cfg.features.complaints && text.match(/не (?:работает|подключ\w*|грузит)\s+(?:сервер\s+)?([А-ЯЁA-Z][\p{L}-]{2,20})/u)
  if (cm) {
    const name = cm[1][0].toUpperCase() + cm[1].slice(1).toLowerCase(), list = (await kget(env, 'cmp', [])).filter(x => now() - x.at < 86400000)
    if (!list.some(x => x.uid === m.from.id && x.name === name)) list.push({ name, uid: m.from.id, at: now() })
    await kput(env, 'cmp', list.slice(-300))
    const hour = list.filter(x => x.name === name && now() - x.at < 3600000).length
    if (hour === 3 && env.OWNER_ID) await tg(env, 'sendMessage', { chat_id: env.OWNER_ID, text: `⚠️ Жалобы на сервер «${name}»: ${hour} человека за час. Проверьте его — и, если надо, поставьте «Обслуживание» в пульте.` })
    return tg(env, 'sendMessage', { chat_id: m.chat.id, text: 'Спасибо, записал жалобу на «' + name + '» 🙏 Пока попробуйте автопилот или другой сервер. Состояние серверов — /status', reply_markup: after(cfg, en) })
  }
  const f = findFaq(cfg, text, en)
  if (f) return answer(env, cfg, m.chat.id, f, en)
  await count(env, cfg, '_miss')
  if (!owner && await forwardToOwner(env, cfg, m, en, 'text')) return
  return tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? EN.notFound : fill(cfg, cfg.notFound), reply_markup: { inline_keyboard: [...menu(cfg, en).inline_keyboard, [{ text: en ? EN.support : '💬 Написать в поддержку', url: cfg.support }]] } })
}
const lastTopic = new Map() // память разговора: uid → {тема, время} (в памяти — без записей в KV)
const QUIZ = [ // викторина: как пользоваться приложением
  ['Что делает автопилот?', ['Сам выбирает рабочий сервер', 'Выключает интернет', 'Удаляет подписку'], 0, 'Автопилот проверяет серверы и подключается к лучшему.'],
  ['Где обновить подписку?', ['Настройки → Подписка → «Обновить»', 'В магазине приложений', 'Нигде, она вечная'], 0, 'После продления нажмите «Обновить» — серверы подтянутся.'],
  ['Что делать, если не подключается?', ['Открыть «Проверка сети» и прислать итог', 'Переустановить телефон', 'Ждать неделю'], 0, '«Проверка сети» на главном покажет, где проблема, — итог можно сразу отправить в поддержку.'],
  ['Как узнать, что вышла новая версия?', ['Приложение покажет плашку «Вышла версия»', 'Никак', 'Только по СМС'], 0, 'Приложение само проверяет обновления; бот тоже подскажет, если написать «у меня 3.20».'],
  ['Можно ли пересылать свою ссылку подписки?', ['Нет, это ваш личный ключ', 'Да, всем друзьям', 'Только в общий чат'], 0, 'Ссылка подписки — личная. Друзей лучше пригласить через /invite.']]
function quizMsg(i, score) { const q = QUIZ[i]; return { text: `🧠 Викторина — вопрос ${i + 1} из ${QUIZ.length}\n\n${q[0]}`, reply_markup: { inline_keyboard: q[1].map((o, j) => [{ text: o, callback_data: `qz:${i}:${j}:${score}` }]) } } }
async function answer(env, cfg, chat, f, en) { // ответ по теме — с картинкой, если она задана в пульте
  if (cfg.features.memory) { lastTopic.set(String(chat), { t: f.title, at: now() }); if (lastTopic.size > 5000) lastTopic.delete(lastTopic.keys().next().value) }
  await count(env, cfg, f.id)
  const img = !en && cfg.features.images && cfg.images[f.id], t = fill(cfg, f.text)
  if (img) {
    if (t.length <= 1000) return tg(env, 'sendPhoto', { chat_id: chat, photo: img, caption: t, reply_markup: after(cfg, en) })
    await tg(env, 'sendPhoto', { chat_id: chat, photo: img })
  }
  return tg(env, 'sendMessage', { chat_id: chat, text: t, disable_web_page_preview: true, reply_markup: after(cfg, en) })
}

// Защита лички владельца от спама: не больше 3 пересланных вопросов от одного человека за час
async function fwAllowed(env, uid) {
  try {
    const k = 'fl:' + uid, n = +(await env.APEX_KV.get(k) || 0)
    if (n >= 3) return false
    await env.APEX_KV.put(k, String(n + 1), { expirationTtl: 3600 })
    return true
  } catch { return false }
}

const who = u => u.username ? '@' + u.username : (u.name || 'участник')

// Конкурсы: c:<номер> → { ids: [номера], users: {номер: {name, username}}, closed, winners }
async function join(env, cq, cid) {
  if (!env.APEX_KV) return tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: 'Конкурс пока не настроен', show_alert: true })
  const key = 'c:' + cid.replace(/[^\w-]/g, '').slice(0, 40)
  const c = JSON.parse(await env.APEX_KV.get(key) || '{"ids":[],"users":{}}')
  if (c.closed) return tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: 'Конкурс уже завершён 🙌', show_alert: true })
  const id = String(cq.from.id), cfg = await config(env)
  if (c.ids.includes(id)) return tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: 'Вы уже участвуете — удачи! 🍀' })
  // Только подписчики канала, где стоит пост (бот должен быть админом канала)
  if (cfg.features.contestSub && cq.message?.chat?.type === 'channel') {
    const st = (await tg(env, 'getChatMember', { chat_id: cq.message.chat.id, user_id: cq.from.id })).result?.status
    if (st && !['member', 'administrator', 'creator'].includes(st)) return tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: 'Чтобы участвовать, подпишитесь на канал 🙌', show_alert: true })
  }
  // По желанию: без пустых аккаунтов — нужно фото профиля или @имя (Telegram не сообщает возраст аккаунта)
  if (cfg.features.contestReal && !cq.from.username) {
    const ph = (await tg(env, 'getUserProfilePhotos', { user_id: cq.from.id, limit: 1 })).result?.total_count || 0
    if (!ph) return tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: 'Участвовать могут аккаунты с фото профиля или @именем 🙏', show_alert: true })
  }
  c.ids.push(id); c.users[id] = { name: [cq.from.first_name, cq.from.last_name].filter(Boolean).join(' ').slice(0, 40), username: cq.from.username || '' }
  await env.APEX_KV.put(key, JSON.stringify(c))
  await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: 'Вы участвуете! Удачи 🍀' })
  // Число участников на кнопке (не чаще, чем пускает Telegram — ошибки не важны)
  const btn = cq.message?.reply_markup?.inline_keyboard?.[0]?.[0]
  if (cq.message && btn) await tg(env, 'editMessageReplyMarkup', { chat_id: cq.message.chat.id, message_id: cq.message.message_id, reply_markup: { inline_keyboard: [[{ text: btn.text.replace(/\s*\(\d+\)$/, '') + ` (${c.ids.length})`, callback_data: btn.callback_data }]] } })
}

async function draw(env, cid, n) {
  if (!env.APEX_KV) return { error: 'нет привязки APEX_KV' }
  const key = 'c:' + String(cid || '').replace(/[^\w-]/g, '').slice(0, 40)
  const c = JSON.parse(await env.APEX_KV.get(key) || 'null')
  if (!c) return { count: 0, winners: [] }
  if (c.closed && c.winners) return { count: c.ids.length, winners: c.winners }
  const pool = [...c.ids], winners = []
  while (winners.length < Math.min(Math.max(1, n), 10) && pool.length) {
    const r = new Uint32Array(1); crypto.getRandomValues(r) // честный случайный выбор
    const [id] = pool.splice(r[0] % pool.length, 1)
    winners.push({ id, ...c.users[id] })
  }
  c.closed = true; c.winners = winners
  await env.APEX_KV.put(key, JSON.stringify(c))
  // Победителям — поздравление в личку (дойдёт тем, кто хоть раз писал боту)
  if ((await config(env)).features.contestDM) for (const w of winners) await tg(env, 'sendMessage', { chat_id: w.id, text: '🎉 Поздравляем! Вы победили в конкурсе APEX CLIENT. Напишите в поддержку, чтобы получить приз: ' + (await config(env)).support }).catch?.(() => {})
  return { count: c.ids.length, winners }
}

// ── Раз в минуту (свой сервер) или на каждом сообщении (Cloudflare): рассылка, «Помогло?», меню команд и описание
let tickAt = 0
export async function tick(env, force) {
  if (!env.APEX_KV || (!force && now() - tickAt < 60000)) return
  tickAt = now()
  const cfg = await config(env)
  // Меню команд «/» и описание бота — когда поменялись в пульте
  const sig = JSON.stringify([cfg.features, cfg.description, cfg.short])
  if ((await kget(env, 'sig', '')) !== sig && !cfg.features.commands) { await tg(env, 'deleteMyCommands', {}); if (env.OWNER_ID) await tg(env, 'deleteMyCommands', { scope: { type: 'chat', chat_id: +env.OWNER_ID } }); await kput(env, 'sig', sig) }
  if ((await kget(env, 'sig', '')) !== sig) {
    const cmds = [['start', 'Начать / темы'], ['download', 'Скачать приложение'], ...(cfg.features.status ? [['status', 'Состояние серверов']] : []), ...(cfg.features.news ? [['news', 'Последние новости']] : []), ...(cfg.features.invite ? [['invite', 'Пригласить друга']] : []), ...(cfg.features.remind ? [['remind', 'Напоминание о продлении']] : []), ...(cfg.features.complaints ? [['problem', 'Сообщить о проблеме с сервером']] : []), ...(cfg.features.form ? [['report', 'Форма: что не работает']] : []), ...(cfg.features.quiz ? [['quiz', 'Викторина по приложению']] : []), ['stop', 'Не получать рассылки']]
    await tg(env, 'setMyCommands', { commands: cmds.map(([command, description]) => ({ command, description })) })
    if (env.OWNER_ID) await tg(env, 'setMyCommands', { scope: { type: 'chat', chat_id: +env.OWNER_ID }, commands: [...cmds, ['today', 'Что было за сутки'], ['queue', 'Вопросы без ответа'], ['top', 'Частые вопросы'], ['users', 'Пользователи'], ['settings', 'Включить и выключить функции'], ['ping', 'Работает ли бот'], ['stats', 'Статистика тем'], ['contests', 'Конкурсы'], ['help', 'Команды владельца']].map(x => Array.isArray(x) ? { command: x[0], description: x[1] } : x) })
    if (cfg.description) await tg(env, 'setMyDescription', { description: cfg.description })
    if (cfg.short) await tg(env, 'setMyShortDescription', { short_description: cfg.short })
    await kput(env, 'sig', sig)
  }
  // Капча: кто не нажал «Я не робот» за 5 минут — удаляем из группы (сможет зайти снова)
  { const cap = await kget(env, 'capt', []), old = cap.filter(c => now() - c.at > 300000)
    if (old.length) { await kput(env, 'capt', cap.filter(c => now() - c.at <= 300000)); for (const c of old.slice(0, 10)) { await tg(env, 'banChatMember', { chat_id: c.chat, user_id: c.uid, until_date: Math.floor(now() / 1000) + 60 }); await tg(env, 'unbanChatMember', { chat_id: c.chat, user_id: c.uid, only_if_banned: true }); if (c.mid) await tg(env, 'deleteMessage', { chat_id: c.chat, message_id: c.mid }) } } }
  // Годовщина: в 12:00 МСК поздравляем тех, кто написал боту впервые ровно год (два, три…) назад — по 40 карточек за шаг
  if (cfg.features.anniv) {
    const msk = new Date(now() + 3 * 3600000), day = msk.toISOString().slice(0, 10), md = day.slice(5)
    if (msk.getUTCHours() >= 12) {
      const st = await kget(env, 'anv', { day: '', i: 0 })
      if (st.day !== day) { st.day = day; st.i = 0; st.done = false }
      if (!st.done) {
        const ids = await kget(env, 'users', [])
        for (const id of ids.slice(st.i, st.i + 40)) { const c = await kget(env, 'u:' + id, null), f = c?.first && new Date(c.first + 3 * 3600000).toISOString().slice(0, 10), y = f ? +day.slice(0, 4) - +f.slice(0, 4) : 0
          if (f && f.slice(5) === md && y >= 1) await tg(env, 'sendMessage', { chat_id: id, text: `🎉 ${y === 1 ? 'Год' : y + (y < 5 ? ' года' : ' лет')} с APEX CLIENT! Спасибо, что вы с нами 💙\nЕсли что-то не работает — просто напишите сюда.` }) }
        st.i += 40; if (st.i >= ids.length) st.done = true; await kput(env, 'anv', st)
      }
    }
  }
  // Конкурсы из бота: итоги в назначенное время — в канал и владельцу
  if (cfg.features.contestBot) {
    const auto = await kget(env, 'cauto', []), due = auto.filter(a => a.at <= now())
    if (due.length) {
      await kput(env, 'cauto', auto.filter(a => a.at > now()))
      for (const a of due) {
        const r = await draw(env, a.cid, a.n), names = r.winners?.map(who).join(', ') || 'участников не было'
        if (cfg.channel) await tg(env, 'sendMessage', { chat_id: cfg.channel, text: `🏆 Итоги конкурса\nУчастников: ${r.count || 0}\nПобедители: ${names}\n\nПоздравляем! 🎉`, ...(a.mid ? { reply_parameters: { message_id: a.mid, allow_sending_without_reply: true } } : {}) })
        if (env.OWNER_ID) await tg(env, 'sendMessage', { chat_id: env.OWNER_ID, text: `🏆 Конкурс ${a.cid}: участников ${r.count || 0}, победители: ${names}` })
      }
    }
  }
  // Утренняя сводка владельцу в 9:00 МСК (раз в день)
  if (cfg.features.morning && env.OWNER_ID) {
    const msk = new Date(now() + 3 * 3600000), day = msk.toISOString().slice(0, 10)
    if (msk.getUTCHours() >= 9 && (await kget(env, 'mday', '')) !== day) { await kput(env, 'mday', day); await tg(env, 'sendMessage', { chat_id: env.OWNER_ID, text: '☀️ Доброе утро! ' + (await todayText(env)).replace('📊 Сегодня', 'Сводка за сутки'), reply_markup: { inline_keyboard: [[{ text: '📥 Вопросы', callback_data: 'ad:q' }, { text: '🛠 Админ', callback_data: 'ad:menu' }]] } }) }
  }
  // «Помогло?» через день после ответа
  const fu = await kget(env, 'fu', {}); let ch = false
  for (const [uid, x] of Object.entries(fu)) if (x.at <= now()) {
    if (cfg.features.followup) await tg(env, 'sendMessage', { chat_id: uid, text: x.en ? 'Did our answer help? 🙂' : 'Помог ли ответ поддержки? 🙂', reply_markup: { inline_keyboard: [[{ text: x.en ? '👍 Yes' : '👍 Да', callback_data: 'fb:1:' + x.qid }, { text: x.en ? '👎 No' : '👎 Нет', callback_data: 'fb:0:' + x.qid }]] } })
    delete fu[uid]; ch = true
  }
  if (ch) await kput(env, 'fu', fu)
  // Рассылка / опрос из пульта: по 25 человек за раз (у Cloudflare лимит запросов), продолжаем на следующем шаге
  let bcBusy = false // за один шаг — не больше одной рассылки (лимит запросов Cloudflare)
  for (const bc of [cfg.broadcast, await kget(env, 'bcadm', null), await kget(env, 'polladm', null)]) if (bc && cfg.features.broadcast && !bcBusy) {
    const k = 'bc:' + bc.id, st = await kget(env, k, { i: 0, ok: 0, fail: 0, polls: [] })
    const users = await kget(env, 'users', [])
    if (bc.poll && !cfg.features.polls) st.done = true
    if (!st.done && (!bc.at || bc.at <= now())) { bcBusy = true
      for (const uid of users.slice(st.i, st.i + 25)) {
        const r = bc.poll ? await tg(env, 'sendPoll', { chat_id: uid, question: String(bc.poll.q).slice(0, 300), options: bc.poll.options.slice(0, 10).map(o => ({ text: String(o).slice(0, 100) })), is_anonymous: true }) : await tg(env, 'sendMessage', { chat_id: uid, text: fill(cfg, String(bc.text)).slice(0, 4000) + '\n\n/stop — не получать рассылки', disable_web_page_preview: true })
        if (r.ok) { st.ok++; if (bc.poll && r.result?.poll?.id) st.polls.push(r.result.poll.id); st.polls = st.polls.slice(-500) } else st.fail++
      }
      st.i += 25; if (st.i >= users.length) { st.done = true; st.at = now() }
      await kput(env, k, st)
    }
  }
}

// ── Данные для пульта (зашифрованы словом WEBHOOK_SECRET): вопросы, статистика, рассылки, опросы, приглашения, жалобы
async function dataExport(env) {
  const cfg = await config(env), st = {}
  for (let i = 0; i < 7; i++) { const day = new Date(now() - i * 86400000).toISOString().slice(0, 10); st[day] = await kget(env, 'st:' + day, {}) }
  const bc = cfg.broadcast ? await kget(env, 'bc:' + cfg.broadcast.id, null) : null
  const refc = await kget(env, 'refc', {})
  return { at: now(), host: env.HOST_INFO || 'Cloudflare', users: (await kget(env, 'users', [])).length, stop: (await kget(env, 'stop', [])).length, remind: await kget(env, 'rmd', []),
    q: await kget(env, 'q', []), stats: st, fb: await kget(env, 'fbk', []), cmp: (await kget(env, 'cmp', [])).map(x => ({ name: x.name, at: x.at })), spam: await kget(env, 'spam', 0),
    broadcast: bc && { id: cfg.broadcast.id, ok: bc.ok, fail: bc.fail, done: !!bc.done }, polls: await kget(env, 'pollres', {}),
    refs: Object.entries(refc).sort((a, b) => b[1] - a[1]).slice(0, 20), features: cfg.features, fover: await kget(env, 'fover', {}),
    helpers: await kget(env, 'helpers', []), by: await answeredBy(env), ulist: await userList(env, 300), block: await kget(env, 'block', []), stopl: (await kget(env, 'stop', [])).slice(-500) }
}
async function hmac(secret, msg) { const k = await crypto.subtle.importKey('raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']); return [...new Uint8Array(await crypto.subtle.sign('HMAC', k, new TextEncoder().encode(msg)))].map(b => b.toString(16).padStart(2, '0')).join('') }
async function seal(secret, obj) { // AES-GCM, ключ = SHA-256('apex-bot:' + слово) — то же делает робот
  const key = await crypto.subtle.importKey('raw', await crypto.subtle.digest('SHA-256', new TextEncoder().encode('apex-bot:' + secret)), 'AES-GCM', false, ['encrypt'])
  const iv = crypto.getRandomValues(new Uint8Array(12)), ct = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(JSON.stringify(obj))))
  const b64 = a => btoa(String.fromCharCode(...a)); return { i: b64(iv), c: b64(ct) }
}
const okSig = async (env, req) => { const ts = +req.headers.get('x-ts'), sig = req.headers.get('x-sig') || ''; return Math.abs(now() - ts) < 300000 && sig.length === 64 && sig === await hmac(env.WEBHOOK_SECRET, String(ts)) }

export const ALLOWED = ['message', 'callback_query', 'poll']
export { FEATURES, FEAT_NAMES, FEAT_GROUPS, CONFIG_URL } // для меню сервера (apex → «Функции бота»)
export default {
  // Cloudflare: расписание раз в минуту (ставит «Применить к боту в Cloudflare») — рассылка, «Помогло?», меню команд
  async scheduled(event, env, ctx) { if (env.BOT_TOKEN) ctx.waitUntil(tick(env, true).catch(() => {})) },
  async fetch(req, env) {
    const url = new URL(req.url)
    if (!env.BOT_TOKEN || !env.WEBHOOK_SECRET) return new Response('Добавьте секреты BOT_TOKEN и WEBHOOK_SECRET', { status: 500 })
    // Однократная настройка: адрес бота для Telegram
    if (url.pathname === '/setup') {
      if (url.searchParams.get('key') !== env.WEBHOOK_SECRET) return new Response('Неверный ключ', { status: 403 })
      const r = await tg(env, 'setWebhook', { url: url.origin + '/tg', secret_token: env.WEBHOOK_SECRET, allowed_updates: ALLOWED, drop_pending_updates: true })
      return new Response(r.ok ? 'Готово! Бот отвечает. Напишите ему /start' : 'Telegram: ' + r.description, { headers: { 'content-type': 'text/plain; charset=utf-8' } })
    }
    // Итоги конкурса — зовёт робот GitHub (секреты BOT_WORKER_URL и BOT_WORKER_KEY)
    if (url.pathname === '/api/draw' && req.method === 'POST') {
      if (req.headers.get('x-key') !== env.WEBHOOK_SECRET && !await okSig(env, req)) return json({ error: 'forbidden' }, 403)
      const b = await req.json().catch(() => ({}))
      return json(await draw(env, b.cid, b.n))
    }
    // Данные для пульта и ответы из пульта — зовёт робот GitHub; подпись HMAC по времени, ответ зашифрован
    if (url.pathname === '/api/data' && req.method === 'POST') {
      if (!await okSig(env, req)) return json({ error: 'forbidden' }, 403)
      return json(await seal(env.WEBHOOK_SECRET, await dataExport(env)))
    }
    if (url.pathname === '/api/admin' && req.method === 'POST') { // управление людьми из пульта (через робота)
      if (!await okSig(env, req)) return json({ error: 'forbidden' }, 403)
      const b = await req.json().catch(() => ({}))
      return json(await userAction(env, String(b.act || ''), b.uid, b.text))
    }
    if (url.pathname === '/api/reply' && req.method === 'POST') {
      if (!await okSig(env, req)) return json({ error: 'forbidden' }, 403)
      const b = await req.json().catch(() => ({})), cfg = await config(env), q = (await kget(env, 'q', [])).find(x => x.id === +b.qid)
      if (!q) return json({ error: 'вопрос не найден' })
      const r = await sendToUser(env, cfg, q.uid, String(b.text || '').slice(0, 4000), q.id)
      if (r.ok) await markAnswered(env, q.id, 'пульт')
      return json(r.ok ? { ok: true } : { error: r.description || 'не отправилось' })
    }
    if (url.pathname === '/tg' && req.method === 'POST') {
      if (req.headers.get('x-telegram-bot-api-secret-token') !== env.WEBHOOK_SECRET) return new Response('forbidden', { status: 403 })
      const u = await req.json().catch(() => ({}))
      try {
        if (u.message) await onMessage(env, u.message)
        if (u.poll) { const pr = await kget(env, 'pollres', {}), p = u.poll; pr[p.id] = { q: p.question, o: p.options.map(o => [o.text, o.voter_count]), at: now() }; const ks = Object.keys(pr); if (ks.length > 300) delete pr[ks[0]]; await kput(env, 'pollres', pr) }
        if (u.callback_query) {
          const cq = u.callback_query, d = cq.data || '', owner = env.OWNER_ID && String(cq.from.id) === String(env.OWNER_ID)
          if (d.startsWith('join:')) await join(env, cq, d.slice(5))
          else if (d.startsWith('cap:')) { // капча: нажать может только сам новичок
            if (String(cq.from.id) !== d.slice(4)) await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: 'Эта кнопка — для новичка 🙂' })
            else { const cap = await kget(env, 'capt', []), chat = cq.message?.chat?.id; await kput(env, 'capt', cap.filter(c => !(c.uid === cq.from.id && c.chat === chat))); await tg(env, 'restrictChatMember', { chat_id: chat, user_id: cq.from.id, permissions: ALL_PERMS }); await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: 'Добро пожаловать! 🙌' }); if (cq.message) await tg(env, 'deleteMessage', { chat_id: chat, message_id: cq.message.message_id }) }
          }
          // Кнопки владельца (функции, быстрые ответы): у остальных молча ничего не делают и ничего не показывают
          else if (d.startsWith('ad:') && !owner && await isHelper(env, await config(env), cq.from.id)) await onAdmin(env, await config(env), cq, d) // помощник: onAdmin пускает только к вопросам
          else if ((d.startsWith('ft:') || d.startsWith('fs:') || d.startsWith('qr:') || d.startsWith('ad:')) && !owner && !(d.startsWith('qr:') && await isHelper(env, await config(env), cq.from.id))) await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id })
          else if (d.startsWith('ad:') && owner) await onAdmin(env, await config(env), cq, d)
          else if (d.startsWith('ft:') && owner) { // /settings: переключить функцию
            const k = d.slice(3), cfg0 = await config(env)
            if (k in cfg0.features) { await setFeature(env, k, !cfg0.features[k]); const cfg = await config(env); await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: (FEAT_NAMES[k] || k) + (cfg.features[k] ? ' — включено' : ' — выключено') }); if (cq.message) await tg(env, 'editMessageReplyMarkup', { chat_id: cq.message.chat.id, message_id: cq.message.message_id, reply_markup: settingsKb(cfg, groupOf(k)) }) }
          }
          else if (d.startsWith('fs:') && owner) { // раздел функций (пусто — список разделов)
            const cfg = await config(env); await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id })
            if (cq.message) await tg(env, 'editMessageReplyMarkup', { chat_id: cq.message.chat.id, message_id: cq.message.message_id, reply_markup: settingsKb(cfg, d.slice(3)) })
          }
          else if (d.startsWith('qr:')) { // быстрый ответ владельца или помощника (чужие отсеяны выше)
            const [, i, fwid] = d.split(':'), cfg = await config(env), to = await env.APEX_KV?.get('fw:' + fwid), hb = owner ? '' : 'помощник ' + (cq.from.username ? '@' + cq.from.username : cq.from.first_name || cq.from.id)
            const done = !owner && (await kget(env, 'q', [])).find(x => x.id === +fwid)?.ans
            const r = done ? { ok: false, description: 'на вопрос уже ответили' } : to && cfg.quick[+i] ? await sendToUser(env, cfg, to, fill(cfg, cfg.quick[+i]), +fwid, hb) : { ok: false, description: 'вопрос устарел' }
            await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: r.ok ? '✅ Отправлено' : '⚠️ ' + (r.description || 'не отправилось'), show_alert: !r.ok })
          }
          else if (d.startsWith('fb:')) { // «Помогло?»
            const [, ok, qid] = d.split(':'), l = await kget(env, 'fbk', []); l.push({ ok: ok === '1', qid: +qid, at: now() }); await kput(env, 'fbk', l.slice(-300))
            await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: ok === '1' ? 'Спасибо! 🙌' : 'Жаль 😔 Напишите, что не так — передам поддержке' })
            if (ok !== '1' && env.OWNER_ID) await tg(env, 'sendMessage', { chat_id: env.OWNER_ID, text: '👎 Ответ не помог: ' + who({ username: cq.from.username, name: cq.from.first_name }) })
            if (cq.message) await tg(env, 'editMessageReplyMarkup', { chat_id: cq.message.chat.id, message_id: cq.message.message_id, reply_markup: { inline_keyboard: [] } })
          }
          else if (d.startsWith('qz:')) { // викторина
            const [, i, j, sc] = d.split(':').map(Number), q = QUIZ[i]; await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: q && j === q[2] ? '✅ Верно!' : '❌ Не совсем' })
            if (q && cq.message) { const score = sc + (j === q[2] ? 1 : 0), head = `${j === q[2] ? '✅' : '❌'} ${q[0]}\nОтвет: ${q[1][q[2]]}. ${q[3]}`
              if (i + 1 < QUIZ.length) { const n = quizMsg(i + 1, score); await tg(env, 'editMessageText', { chat_id: cq.message.chat.id, message_id: cq.message.message_id, text: head + '\n\n' + n.text, reply_markup: n.reply_markup }) }
              else await tg(env, 'editMessageText', { chat_id: cq.message.chat.id, message_id: cq.message.message_id, text: `${head}\n\n🏁 Готово! Правильных ответов: ${score} из ${QUIZ.length}${score === QUIZ.length ? ' — вы знаете APEX CLIENT на отлично 🏆' : ''}\nЕщё раз — /quiz` }) }
          }
          else if (d === 'human') { const cfg = await config(env), en = isEn(cfg, cq.from); await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id }); if (cfg.features.human) { await kput(env, 'hw:' + cq.from.id, 1, 1800); await tg(env, 'sendMessage', { chat_id: cq.message.chat.id, text: en ? '🙋 Write your question in one message — I will pass it to a person.' : '🙋 Напишите вопрос одним сообщением — передам человеку из поддержки.' }) } }
          else if (d === 'menu' || d === 'menuen') { const cfg = await config(env), en = d === 'menuen'; await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id }); await tg(env, 'sendMessage', { chat_id: cq.message.chat.id, text: en ? EN.pick : 'Выберите тему:', reply_markup: menu(cfg, en) }) }
          else if (d.startsWith('faq:') || d.startsWith('faqen:')) {
            const en = d.startsWith('faqen:'), cfg = await config(env), f = topicsOf(cfg, en).find(x => x.id === d.slice(en ? 6 : 4))
            await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id })
            if (f) await answer(env, cfg, cq.message.chat.id, f, en)
          }
        }
      } catch (e) { await logErr(env, u.message ? 'сообщение' : u.callback_query ? 'кнопка' : 'обновление', e) } // Telegram не должен повторять запрос
      try { await tick(env) } catch (e) { await logErr(env, 'tick', e) }
      return new Response('ok')
    }
    return new Response('APEX CLIENT bot', { headers: { 'content-type': 'text/plain; charset=utf-8' } })
  },
}
