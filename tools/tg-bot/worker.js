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
  }
  cfgAt = Date.now()
  return cfgCache
}
const fill = (cfg, t) => t.replaceAll('{сайт}', SITE).replaceAll('{поддержка}', cfg.support)
const menu = cfg => ({ inline_keyboard: cfg.topics.map(f => [{ text: f.title, callback_data: 'faq:' + f.id }]) })
const after = cfg => ({ inline_keyboard: [[{ text: '📋 Другие темы', callback_data: 'menu' }], [{ text: '💬 Написать в поддержку', url: cfg.support }]] })

function findFaq(cfg, text) {
  const t = (text || '').toLowerCase()
  let best = null, score = 0
  for (const f of cfg.topics) {
    const s = f.words.filter(w => t.includes(w)).length
    if (s > score) { best = f; score = s }
  }
  return best
}

async function onMessage(env, m) {
  if (m.chat?.type !== 'private') return // в группах и каналах молчим
  const cfg = await config(env)
  const text = (m.text || '').trim(), owner = env.OWNER_ID && String(m.from?.id) === String(env.OWNER_ID)
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
  if (text === '/start' || text === '/help' || !text) {
    return tg(env, 'sendMessage', { chat_id: m.chat.id, text: fill(cfg, cfg.greeting), reply_markup: menu(cfg) })
  }
  const f = findFaq(cfg, text)
  if (f) return tg(env, 'sendMessage', { chat_id: m.chat.id, text: fill(cfg, f.text), disable_web_page_preview: true, reply_markup: after(cfg) })
  return tg(env, 'sendMessage', { chat_id: m.chat.id, text: fill(cfg, cfg.notFound), reply_markup: { inline_keyboard: [...menu(cfg).inline_keyboard, [{ text: '💬 Написать в поддержку', url: cfg.support }]] } })
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
          else if (d === 'menu') { const cfg = await config(env); await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id }); await tg(env, 'sendMessage', { chat_id: cq.message.chat.id, text: 'Выберите тему:', reply_markup: menu(cfg) }) }
          else if (d.startsWith('faq:')) {
            const cfg = await config(env), f = cfg.topics.find(x => x.id === d.slice(4))
            await tg(env, 'answerCallbackQuery', { callback_query_id: cq.id })
            if (f) await tg(env, 'sendMessage', { chat_id: cq.message.chat.id, text: fill(cfg, f.text), disable_web_page_preview: true, reply_markup: after(cfg) })
          }
        }
      } catch (e) { /* ответим на следующее сообщение; Telegram не должен повторять запрос */ }
      return new Response('ok')
    }
    return new Response('APEX CLIENT bot', { headers: { 'content-type': 'text/plain; charset=utf-8' } })
  },
}
