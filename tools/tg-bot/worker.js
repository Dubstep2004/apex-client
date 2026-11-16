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

async function tg(env, method, body) {
  const r = await fetch(`https://api.telegram.org/bot${env.BOT_TOKEN}/${method}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) })
  return r.json()
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
    topics: topics.length ? topics : FAQ.map(f => ({ ...f, text: f.text || 'Поддержка поможет с подключением, подпиской и продлением: {поддержка}' })),
    // Функции бота: пульт → «Функции бота». Нет настройки — значение по умолчанию
    features: Object.fromEntries(Object.entries({ status: true, ping: true, english: true, forward: false, stats: true }).map(([k, d]) => [k, typeof c?.features?.[k] === 'boolean' ? c.features[k] : d])),
  }
  cfgAt = Date.now()
  return cfgCache
}
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

async function onMessage(env, m) {
  if (m.chat?.type !== 'private') return // в группах и каналах молчим
  const cfg = await config(env)
  const text = (m.text || '').trim(), owner = env.OWNER_ID && String(m.from?.id) === String(env.OWNER_ID), en = isEn(cfg, m.from)
  const cmd = text.split(/[\s@]/)[0].toLowerCase()
  // Владелец ответил на пересланный вопрос — отправляем ответ человеку
  if (owner && m.reply_to_message && env.APEX_KV) {
    const to = await env.APEX_KV.get('fw:' + m.reply_to_message.message_id)
    if (to) {
      const r = await tg(env, 'copyMessage', { chat_id: to, from_chat_id: m.chat.id, message_id: m.message_id })
      return tg(env, 'sendMessage', { chat_id: m.chat.id, text: r.ok ? '✅ Ответ отправлен' : '⚠️ Не отправилось: ' + (r.description || 'ошибка') })
    }
  }
  if (owner && cmd === '/ping' && cfg.features.ping) {
    const t0 = Date.now(); await config(env)
    return tg(env, 'sendMessage', { chat_id: m.chat.id, text: `🏓 Работаю · ${env.HOST_INFO || 'Cloudflare'}\nФункции: ${Object.entries(cfg.features).map(([k, v]) => (v ? '✅' : '▫️') + k).join(' ')}\nОтвет за ${Date.now() - t0} мс` })
  }
  if (owner && cmd === '/stats') return tg(env, 'sendMessage', { chat_id: m.chat.id, text: await statsText(env, cfg) })
  if (owner && cmd === '/help') return tg(env, 'sendMessage', { chat_id: m.chat.id, text: 'Команды владельца:\n/ping — работает ли бот\n/stats — какие темы спрашивают за неделю\n/status — состояние серверов\n/contests — конкурсы\n/draw <номер> [победителей] — подвести итоги\nОтветьте на пересланный вопрос — бот отправит ответ человеку.' })
  if (cmd === '/status' && cfg.features.status) return tg(env, 'sendMessage', { chat_id: m.chat.id, text: await statusText(), disable_web_page_preview: true })
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
  if (cmd === '/start' || cmd === '/help' || (!text && !m.photo && !m.document && !m.voice)) {
    return tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? EN.greeting : fill(cfg, cfg.greeting), reply_markup: menu(cfg, en) })
  }
  const f = findFaq(cfg, text, en)
  if (f) { await count(env, cfg, f.id); return tg(env, 'sendMessage', { chat_id: m.chat.id, text: fill(cfg, f.text), disable_web_page_preview: true, reply_markup: after(cfg, en) }) }
  await count(env, cfg, '_miss')
  // Вопрос без ответа — владельцу (если включено в пульте)
  if (cfg.features.forward && env.OWNER_ID && env.APEX_KV && !owner && await fwAllowed(env, m.from?.id)) {
    const fw = await tg(env, 'forwardMessage', { chat_id: env.OWNER_ID, from_chat_id: m.chat.id, message_id: m.message_id })
    if (fw.ok) {
      await env.APEX_KV.put('fw:' + fw.result.message_id, String(m.chat.id), { expirationTtl: 14 * 86400 })
      await tg(env, 'sendMessage', { chat_id: env.OWNER_ID, reply_to_message_id: fw.result.message_id, text: '❓ Вопрос без готового ответа от ' + who({ username: m.from?.username, name: m.from?.first_name }) + '. Ответьте на пересланное сообщение — бот отправит ответ.' })
      return tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? EN.sent : 'Передал ваш вопрос в поддержку — ответ придёт сюда 🙌', reply_markup: menu(cfg, en) })
    }
  }
  return tg(env, 'sendMessage', { chat_id: m.chat.id, text: en ? EN.notFound : fill(cfg, cfg.notFound), reply_markup: { inline_keyboard: [...menu(cfg, en).inline_keyboard, [{ text: en ? EN.support : '💬 Написать в поддержку', url: cfg.support }]] } })
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
  const id = String(cq.from.id)
  if (c.ids.includes(id)) return tg(env, 'answerCallbackQuery', { callback_query_id: cq.id, text: 'Вы уже участвуете — удачи! 🍀' })
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
  return { count: c.ids.length, winners }
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url)
    if (!env.BOT_TOKEN || !env.WEBHOOK_SECRET) return new Response('Добавьте секреты BOT_TOKEN и WEBHOOK_SECRET', { status: 500 })
    // Однократная настройка: адрес бота для Telegram
    if (url.pathname === '/setup') {
      if (url.searchParams.get('key') !== env.WEBHOOK_SECRET) return new Response('Неверный ключ', { status: 403 })
      const r = await tg(env, 'setWebhook', { url: url.origin + '/tg', secret_token: env.WEBHOOK_SECRET, allowed_updates: ['message', 'callback_query'], drop_pending_updates: true })
      return new Response(r.ok ? 'Готово! Бот отвечает. Напишите ему /start' : 'Telegram: ' + r.description, { headers: { 'content-type': 'text/plain; charset=utf-8' } })
    }
    // Итоги конкурса — зовёт робот GitHub (секреты BOT_WORKER_URL и BOT_WORKER_KEY)
    if (url.pathname === '/api/draw' && req.method === 'POST') {
      if (req.headers.get('x-key') !== env.WEBHOOK_SECRET) return json({ error: 'forbidden' }, 403)
      const b = await req.json().catch(() => ({}))
      return json(await draw(env, b.cid, b.n))
    }
    if (url.pathname === '/tg' && req.method === 'POST') {
      if (req.headers.get('x-telegram-bot-api-secret-token') !== env.WEBHOOK_SECRET) return new Response('forbidden', { status: 403 })
      const u = await req.json().catch(() => ({}))
      try {
        if (u.message) await onMessage(env, u.message)
        if (u.callback_query) {
          const cq = u.callback_query, d = cq.data || ''
          if (d.startsWith('join:')) await join(env, cq, d.slice(5))
          else if (d === 'menu' || d === 'menuen') { const cfg = await config(env), en = d === 'menuen'; await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id }); await tg(env, 'sendMessage', { chat_id: cq.message.chat.id, text: en ? EN.pick : 'Выберите тему:', reply_markup: menu(cfg, en) }) }
          else if (d.startsWith('faq:') || d.startsWith('faqen:')) {
            const en = d.startsWith('faqen:'), cfg = await config(env), f = topicsOf(cfg, en).find(x => x.id === d.slice(en ? 6 : 4))
            await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id })
            if (f) { await count(env, cfg, f.id); await tg(env, 'sendMessage', { chat_id: cq.message.chat.id, text: fill(cfg, f.text), disable_web_page_preview: true, reply_markup: after(cfg, en) }) }
          }
        }
      } catch (e) { /* ответим на следующее сообщение; Telegram не должен повторять запрос */ }
      return new Response('ok')
    }
    return new Response('APEX CLIENT bot', { headers: { 'content-type': 'text/plain; charset=utf-8' } })
  },
}
