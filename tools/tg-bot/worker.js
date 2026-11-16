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

// Функции: по умолчанию ← пульт (bot.json) ← переключено в боте (/settings) или в меню сервера (KV «fover», с временем).
// Что новее — то и действует: сохранили в пульте позже — пульт главнее.
async function mergeFeatures(env, c) {
  const f = Object.fromEntries(Object.entries(FEATURES).map(([k, d]) => [k, typeof c?.features?.[k] === 'boolean' ? c.features[k] : d]))
  const ov = await kget(env, 'fover', {}), pAt = +c?.featuresAt || 0
  for (const [k, o] of Object.entries(ov)) if (k in f && typeof o?.v === 'boolean' && (o.at || 0) > pAt) f[k] = o.v
  return f
}
async function setFeature(env, k, v) { const ov = await kget(env, 'fover', {}); ov[k] = { v, at: now() }; await kput(env, 'fover', ov); cfgCache = null }
const settingsKb = cfg => ({ inline_keyboard: Object.keys(FEAT_NAMES).filter(k => k in cfg.features).reduce((rows, k, i) => { const b = { text: (cfg.features[k] ? '✅ ' : '▫️ ') + FEAT_NAMES[k], callback_data: 'ft:' + k }; if (i % 2) rows[rows.length - 1].push(b); else rows.push([b]); return rows }, []) })

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
    topics: topics.length ? topics : FAQ.map(f => ({ ...f, text: f.text || 'Поддержка поможет с подключением, подпиской и продлением: {поддержка}' })),
    // Функции бота: пульт → «Функции бота». Нет настройки — значение по умолчанию
    features: await mergeFeatures(env, c),
    // 2.0: быстрые ответы владельца, описание бота, рассылка/опрос, картинки к темам
    quick: (Array.isArray(c?.quick) ? c.quick : QUICK).map(x => String(x).slice(0, 300)).filter(Boolean).slice(0, 6),
    description: String(c?.description || '').slice(0, 512), short: String(c?.short || '').slice(0, 120),
    broadcast: c?.broadcast && /^[\w-]{4,40}$/.test(String(c.broadcast.id || '')) ? c.broadcast : null,
    images: Object.fromEntries((Array.isArray(c?.topics) ? c.topics : []).filter(t => t?.id && /^https:\/\/\S+$/.test(t.image || '')).map(t => [String(t.id).replace(/[^\w-]/g, '').slice(0, 24), t.image])),
    channel: /^@?\w{4,}$/.test(String(c?.channel || '')) ? String(c.channel) : '',
    welcome: String(c?.welcome || 'Добро пожаловать, {имя}! 👋\nЗдесь новости и помощь по APEX CLIENT.').slice(0, 600),
  }
  cfgAt = Date.now()
  return cfgCache
}
const FEATURES = { media: true, quick: true, commands: true, images: true, deeplinks: true, contestDM: true, polls: true, broadcast: true, news: true, status: true, ping: true, english: true, forward: false, stats: true, welcome: true, antispam: true, invite: true, remind: true, followup: true, complaints: true, translate: true, antiflood: true, version: true, contestSub: true, contestReal: false }
// Названия функций — для /settings в боте и меню сервера (apex → «Функции бота»)
const FEAT_NAMES = { forward: 'Вопросы без ответа — мне', media: 'Фото и голосовые — мне', quick: 'Быстрые ответы', followup: '«Помогло?»', translate: 'Перевод моих ответов', english: 'Ответы на английском', status: '/status', news: '/news', invite: '/invite', remind: '/remind', complaints: 'Жалобы на серверы', version: 'Подсказка версии', broadcast: 'Рассылка', polls: 'Опросы', stats: 'Счёт тем', ping: '/ping', commands: 'Меню команд «/»', images: 'Картинки в ответах', deeplinks: 'Ссылки на темы', antiflood: 'Защита от флуда', welcome: 'Приветствие в группе', antispam: 'Антиспам в группе', contestSub: 'Конкурс: только подписчики', contestReal: 'Конкурс: без пустых аккаунтов', contestDM: 'Конкурс: поздравить победителей' }
const QUICK = ['Обновите подписку: Настройки → Подписка → «Обновить»', 'Включите автопилот — он сам выберет рабочий сервер', 'Откройте «Проверка сети» на главном и пришлите итог (кнопка «Поделиться»)', 'Обновите приложение до последней версии: ' + SITE + '/update/']
const fill = (cfg, t) => t.replaceAll('{сайт}', SITE).replaceAll('{поддержка}', cfg.support)
const topicsOf = (cfg, en) => en ? FAQ_EN : cfg.topics
const menu = (cfg, en) => ({ inline_keyboard: topicsOf(cfg, en).map(f => [{ text: f.title, callback_data: (en ? 'faqen:' : 'faq:') + f.id }]) })
const after = (cfg, en) => ({ inline_keyboard: [[{ text: en ? EN.other : '📋 Другие темы', callback_data: en ? 'menuen' : 'menu' }], [{ text: en ? EN.support : '💬 Написать в поддержку', url: cfg.support }]] })

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
  const t = (text || '').toLowerCase()
  let best = null, score = 0
  for (const f of topicsOf(cfg, en)) {
    const s = f.words.filter(w => t.includes(w)).length
    if (s > score) { best = f; score = s }
  }
  return best
}

// ── 2.0: пользователи бота (для рассылки), флуд, версии, новости, перевод
const flood = new Map() // в памяти: uid → [время сообщений за минуту]
function flooded(uid) {
  const t = now(), a = (flood.get(uid) || []).filter(x => t - x < 60000); a.push(t); flood.set(uid, a)
  if (flood.size > 5000) flood.clear()
  return a.length > 8 ? (a.length === 9 ? 'warn' : 'skip') : ''
}
async function remember(env, u) { // кто писал боту — для рассылки (без /stop)
  const users = await kget(env, 'users', [])
  if (!users.includes(u.id) && !(await kget(env, 'stop', [])).includes(u.id)) { users.push(u.id); await kput(env, 'users', users.slice(-50000)) }
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
async function markAnswered(env, fwid, how) { const l = await kget(env, 'q', []), q = l.find(x => x.id === fwid); if (q) { q.ans = how; q.ansAt = now(); await kput(env, 'q', l) } return q }

async function forwardToOwner(env, cfg, m, en, kind) {
  if (!(cfg.features.forward && env.OWNER_ID && env.APEX_KV && await fwAllowed(env, m.from?.id))) return false
  const fw = await tg(env, 'forwardMessage', { chat_id: env.OWNER_ID, from_chat_id: m.chat.id, message_id: m.message_id })
  if (!fw.ok) return false
  const id = fw.result.message_id
  await env.APEX_KV.put('fw:' + id, String(m.chat.id), { expirationTtl: 14 * 86400 })
  await logQuestion(env, { id, uid: m.chat.id, name: who({ username: m.from?.username, name: m.from?.first_name }), text: (m.text || m.caption || '').slice(0, 500), kind, at: now(), en: !!en })
  const kb = cfg.features.quick ? cfg.quick.map((q, i) => [{ text: '⚡ ' + q.slice(0, 40), callback_data: `qr:${i}:${id}` }]) : []
  await tg(env, 'sendMessage', { chat_id: env.OWNER_ID, reply_to_message_id: id, text: (kind === 'text' ? '❓ Вопрос без готового ответа' : '📎 Прислали ' + kind) + ' от ' + who({ username: m.from?.username, name: m.from?.first_name }) + (en ? ' (пишет по-английски — ответ переведу)' : '') + '. Ответьте на пересланное сообщение или нажмите быстрый ответ.', reply_markup: { inline_keyboard: kb } })
  await tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? EN.sent : 'Передал ' + (kind === 'text' ? 'ваш вопрос' : kind) + ' в поддержку — ответ придёт сюда 🙌', reply_markup: menu(cfg, en) })
  return true
}
async function sendToUser(env, cfg, to, text, fwid) { // ответ владельца человеку: перевод для англоязычных + «Помогло?» через день
  const q = (await kget(env, 'q', [])).find(x => x.id === fwid)
  let out = text
  if (q?.en && cfg.features.translate && /[а-яё]/i.test(text)) { const t = await translate(env, text); if (t) out = t }
  const r = await tg(env, 'sendMessage', { chat_id: to, text: out, disable_web_page_preview: true })
  if (r.ok) { await markAnswered(env, fwid, 'бот'); if (cfg.features.followup) { const fu = await kget(env, 'fu', {}); fu[to] = { at: now() + 86400000, qid: fwid, en: !!q?.en }; await kput(env, 'fu', fu) } }
  return r
}

async function onGroup(env, cfg, m) { // группа: приветствие и защита от ссылок от новичков
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

async function onMessage(env, m) {
  const cfg = await config(env)
  if (m.chat?.type === 'group' || m.chat?.type === 'supergroup') return onGroup(env, cfg, m)
  if (m.chat?.type !== 'private') return // в каналах молчим
  const text = (m.text || '').trim(), owner = env.OWNER_ID && String(m.from?.id) === String(env.OWNER_ID), en = isEn(cfg, m.from)
  const [cmd0, arg] = text.split(/\s+/, 2), cmd = (cmd0 || '').split('@')[0].toLowerCase()
  if (!owner && cfg.features.antiflood) { const f = flooded(m.from.id); if (f === 'warn') return tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? 'Too many messages — please wait a minute 🙏' : 'Слишком часто — подождите минуту 🙏' }); if (f) return }
  if (!owner && env.APEX_KV) await remember(env, m.from)
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
  if (owner && cmd === '/stats') return tg(env, 'sendMessage', { chat_id: m.chat.id, text: await statsText(env, cfg) })
  if (owner && cmd === '/help') return tg(env, 'sendMessage', { chat_id: m.chat.id, text: 'Команды владельца:\n/settings — включить и выключить функции бота\n/ping — работает ли бот\n/stats — какие темы спрашивают за неделю\n/status — состояние серверов\n/news — последние новости\n/contests — конкурсы\n/draw <номер> [победителей] — подвести итоги\nОтветьте на пересланный вопрос (или нажмите ⚡ быстрый ответ) — бот отправит ответ человеку.' })
  if (cmd === '/status' && cfg.features.status) return tg(env, 'sendMessage', { chat_id: m.chat.id, text: await statusText(), disable_web_page_preview: true })
  if (cmd === '/download' || cmd === '/app') { const v = await latestVersion(); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: (en ? 'APEX CLIENT ' : 'APEX CLIENT ') + (v || '') + (en ? '\nThe file is too big for Telegram (70 MB) — download it from the site:' : '\nФайл больше, чем пропускает Telegram (70 МБ), — скачайте с сайта:'), reply_markup: { inline_keyboard: [[{ text: en ? '⬇️ Download' : '⬇️ Скачать с сайта', url: SITE }], [{ text: en ? 'Mirror (GitHub)' : 'Запасная ссылка (GitHub)', url: 'https://github.com/Dubstep2004/apex-client/releases/latest' }]] } }) }
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
    const f = arg && cfg.features.deeplinks && topicsOf(cfg, en).find(t => t.id === arg)
    if (f) return answer(env, cfg, m.chat.id, f, en)
    return tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? EN.greeting : fill(cfg, cfg.greeting), reply_markup: menu(cfg, en) })
  }
  if (cmd === '/help' || (!text && !m.photo && !m.document && !m.voice && !m.video && !m.video_note)) {
    return tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? EN.greeting : fill(cfg, cfg.greeting), reply_markup: menu(cfg, en) })
  }
  // Незнакомая или выключенная команда — не пересылаем владельцу, показываем темы
  if (text.startsWith('/')) return tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? EN.pick : 'Такой команды нет 🙂 Выберите тему:', reply_markup: menu(cfg, en) })
  // Фото, голосовые, файлы — владельцу
  const kind = m.photo ? 'скриншот' : m.voice || m.video_note ? 'голосовое' : m.document ? 'файл' : m.video ? 'видео' : ''
  if (kind) { if (cfg.features.media && await forwardToOwner(env, cfg, m, en, kind)) return; return tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? EN.notFound : fill(cfg, cfg.notFound), reply_markup: after(cfg, en) }) }
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
async function answer(env, cfg, chat, f, en) { // ответ по теме — с картинкой, если она задана в пульте
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
    const cmds = [['start', 'Начать / темы'], ['download', 'Скачать приложение'], ...(cfg.features.status ? [['status', 'Состояние серверов']] : []), ...(cfg.features.news ? [['news', 'Последние новости']] : []), ...(cfg.features.invite ? [['invite', 'Пригласить друга']] : []), ...(cfg.features.remind ? [['remind', 'Напоминание о продлении']] : []), ...(cfg.features.complaints ? [['problem', 'Сообщить о проблеме с сервером']] : []), ['stop', 'Не получать рассылки']]
    await tg(env, 'setMyCommands', { commands: cmds.map(([command, description]) => ({ command, description })) })
    if (env.OWNER_ID) await tg(env, 'setMyCommands', { scope: { type: 'chat', chat_id: +env.OWNER_ID }, commands: [...cmds, ['settings', 'Включить и выключить функции'], ['ping', 'Работает ли бот'], ['stats', 'Статистика тем'], ['contests', 'Конкурсы'], ['help', 'Команды владельца']].map(x => Array.isArray(x) ? { command: x[0], description: x[1] } : x) })
    if (cfg.description) await tg(env, 'setMyDescription', { description: cfg.description })
    if (cfg.short) await tg(env, 'setMyShortDescription', { short_description: cfg.short })
    await kput(env, 'sig', sig)
  }
  // «Помогло?» через день после ответа
  const fu = await kget(env, 'fu', {}); let ch = false
  for (const [uid, x] of Object.entries(fu)) if (x.at <= now()) {
    if (cfg.features.followup) await tg(env, 'sendMessage', { chat_id: uid, text: x.en ? 'Did our answer help? 🙂' : 'Помог ли ответ поддержки? 🙂', reply_markup: { inline_keyboard: [[{ text: x.en ? '👍 Yes' : '👍 Да', callback_data: 'fb:1:' + x.qid }, { text: x.en ? '👎 No' : '👎 Нет', callback_data: 'fb:0:' + x.qid }]] } })
    delete fu[uid]; ch = true
  }
  if (ch) await kput(env, 'fu', fu)
  // Рассылка / опрос из пульта: по 25 человек за раз (у Cloudflare лимит запросов), продолжаем на следующем шаге
  const bc = cfg.broadcast
  if (bc && cfg.features.broadcast) {
    const k = 'bc:' + bc.id, st = await kget(env, k, { i: 0, ok: 0, fail: 0, polls: [] })
    const users = await kget(env, 'users', [])
    if (bc.poll && !cfg.features.polls) st.done = true
    if (!st.done && (!bc.at || bc.at <= now())) {
      for (const uid of users.slice(st.i, st.i + 25)) {
        const r = bc.poll ? await tg(env, 'sendPoll', { chat_id: uid, question: String(bc.poll.q).slice(0, 300), options: bc.poll.options.slice(0, 10).map(o => ({ text: String(o).slice(0, 100) })), is_anonymous: true }) : await tg(env, 'sendMessage', { chat_id: uid, text: fill(cfg, String(bc.text)).slice(0, 4000) + '\n\n/stop — не получать рассылки', disable_web_page_preview: true })
        if (r.ok) { st.ok++; if (bc.poll) st.polls.push(r.result.poll.id) } else st.fail++
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
    refs: Object.entries(refc).sort((a, b) => b[1] - a[1]).slice(0, 20), features: cfg.features, fover: await kget(env, 'fover', {}) }
}
async function hmac(secret, msg) { const k = await crypto.subtle.importKey('raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']); return [...new Uint8Array(await crypto.subtle.sign('HMAC', k, new TextEncoder().encode(msg)))].map(b => b.toString(16).padStart(2, '0')).join('') }
async function seal(secret, obj) { // AES-GCM, ключ = SHA-256('apex-bot:' + слово) — то же делает робот
  const key = await crypto.subtle.importKey('raw', await crypto.subtle.digest('SHA-256', new TextEncoder().encode('apex-bot:' + secret)), 'AES-GCM', false, ['encrypt'])
  const iv = crypto.getRandomValues(new Uint8Array(12)), ct = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(JSON.stringify(obj))))
  const b64 = a => btoa(String.fromCharCode(...a)); return { i: b64(iv), c: b64(ct) }
}
const okSig = async (env, req) => { const ts = +req.headers.get('x-ts'), sig = req.headers.get('x-sig') || ''; return Math.abs(now() - ts) < 300000 && sig.length === 64 && sig === await hmac(env.WEBHOOK_SECRET, String(ts)) }

export const ALLOWED = ['message', 'callback_query', 'poll']
export { FEATURES, FEAT_NAMES, CONFIG_URL } // для меню сервера (apex → «Функции бота»)
export default {
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
          else if (d.startsWith('ft:') && owner) { // /settings: переключить функцию
            const k = d.slice(3), cfg0 = await config(env)
            if (k in cfg0.features) { await setFeature(env, k, !cfg0.features[k]); const cfg = await config(env); await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: (FEAT_NAMES[k] || k) + (cfg.features[k] ? ' — включено' : ' — выключено') }); if (cq.message) await tg(env, 'editMessageReplyMarkup', { chat_id: cq.message.chat.id, message_id: cq.message.message_id, reply_markup: settingsKb(cfg) }) }
          }
          else if (d.startsWith('qr:') && owner) { // быстрый ответ владельца
            const [, i, fwid] = d.split(':'), cfg = await config(env), to = await env.APEX_KV?.get('fw:' + fwid)
            const r = to && cfg.quick[+i] ? await sendToUser(env, cfg, to, fill(cfg, cfg.quick[+i]), +fwid) : { ok: false, description: 'вопрос устарел' }
            await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: r.ok ? '✅ Отправлено' : '⚠️ ' + (r.description || 'не отправилось'), show_alert: !r.ok })
          }
          else if (d.startsWith('fb:')) { // «Помогло?»
            const [, ok, qid] = d.split(':'), l = await kget(env, 'fbk', []); l.push({ ok: ok === '1', qid: +qid, at: now() }); await kput(env, 'fbk', l.slice(-300))
            await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: ok === '1' ? 'Спасибо! 🙌' : 'Жаль 😔 Напишите, что не так — передам поддержке' })
            if (ok !== '1' && env.OWNER_ID) await tg(env, 'sendMessage', { chat_id: env.OWNER_ID, text: '👎 Ответ не помог: ' + who({ username: cq.from.username, name: cq.from.first_name }) })
            if (cq.message) await tg(env, 'editMessageReplyMarkup', { chat_id: cq.message.chat.id, message_id: cq.message.message_id, reply_markup: { inline_keyboard: [] } })
          }
          else if (d === 'menu' || d === 'menuen') { const cfg = await config(env), en = d === 'menuen'; await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id }); await tg(env, 'sendMessage', { chat_id: cq.message.chat.id, text: en ? EN.pick : 'Выберите тему:', reply_markup: menu(cfg, en) }) }
          else if (d.startsWith('faq:') || d.startsWith('faqen:')) {
            const en = d.startsWith('faqen:'), cfg = await config(env), f = topicsOf(cfg, en).find(x => x.id === d.slice(en ? 6 : 4))
            await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id })
            if (f) await answer(env, cfg, cq.message.chat.id, f, en)
          }
        }
      } catch (e) { /* ответим на следующее сообщение; Telegram не должен повторять запрос */ }
      try { await tick(env) } catch {}
      return new Response('ok')
    }
    return new Response('APEX CLIENT bot', { headers: { 'content-type': 'text/plain; charset=utf-8' } })
  },
}
