// APEX CLIENT — тот же бот, что в Cloudflare (worker.js), но на своём сервере (Ubuntu 24.04, Node.js 18+).
// Ставится одной командой — пульт → Telegram → Настройка → «Бот на своём сервере».
//
// Как работает: сам спрашивает у Telegram новые сообщения (getUpdates) — домен, HTTPS и открытые порты не нужны.
// Конкурсы хранятся в файле kv.json (вместо Cloudflare KV). Тексты бота берутся из пульта, как и в Cloudflare.
// Настройки — /etc/apex-bot.env:
//   BOT_TOKEN      — токен бота от @BotFather (обязательно);
//   OWNER_ID       — по желанию: ваш номер в Telegram (команды /draw и /contests);
//   SUPPORT        — по желанию: ссылка поддержки;
//   WEBHOOK_SECRET — по желанию: секретное слово для /api/draw (нужно, только если включён BOT_PORT);
//   BOT_PORT       — по желанию: порт для итогов конкурсов от робота GitHub (нужен HTTPS-адрес перед ним);
//   NOTIFY=1       — по желанию: сообщать владельцу (OWNER_ID), что бот запущен или остановлен;
//   TG_PROXY       — по желанию: http://[логин:пароль@]адрес:порт, если Telegram с сервера не открывается.
// Одновременно бот может работать только в одном месте: запуск здесь отключает адрес Cloudflare.
import fs from 'node:fs'
import http from 'node:http'
import crypto from 'node:crypto'
import path from 'node:path'
import os from 'node:os'
import https from 'node:https'
import net from 'node:net'
import worker from './worker.js'

const DATA = process.env.BOT_DATA || '/var/lib/apex-bot'
const KV_FILE = path.join(DATA, 'kv.json')
const env = {
  BOT_TOKEN: (process.env.BOT_TOKEN || '').trim(),
  WEBHOOK_SECRET: (process.env.WEBHOOK_SECRET || '').trim() || crypto.randomBytes(24).toString('hex'),
  OWNER_ID: (process.env.OWNER_ID || '').trim(),
  SUPPORT: (process.env.SUPPORT || '').trim(),
  BOT_CONFIG: (process.env.BOT_CONFIG || '').trim(),
  HOST_INFO: 'свой сервер ' + os.hostname(),
}
if (!/^\d+:[\w-]{30,}$/.test(env.BOT_TOKEN)) { console.error('Нет BOT_TOKEN в /etc/apex-bot.env — впишите токен от @BotFather и перезапустите: systemctl restart apex-bot'); process.exit(1) }

// Хранилище конкурсов: то же, что Cloudflare KV (get / put / list), но в одном файле
let kv = {}
try { kv = JSON.parse(fs.readFileSync(KV_FILE, 'utf8')) } catch { kv = {} }
const saveKv = () => { fs.mkdirSync(DATA, { recursive: true }); fs.writeFileSync(KV_FILE + '.tmp', JSON.stringify(kv)); fs.renameSync(KV_FILE + '.tmp', KV_FILE) }
// Срок хранения (expirationTtl) — как в Cloudflare: kv.__exp[ключ] = когда удалить
const alive = k => !(kv.__exp && kv.__exp[k] && kv.__exp[k] < Date.now())
env.APEX_KV = {
  async get(k) { return Object.prototype.hasOwnProperty.call(kv, k) && k !== '__exp' && alive(k) ? kv[k] : null },
  async put(k, v, o = {}) {
    kv[k] = String(v)
    if (o.expirationTtl) { kv.__exp = kv.__exp || {}; kv.__exp[k] = Date.now() + o.expirationTtl * 1000 }
    for (const [x, t] of Object.entries(kv.__exp || {})) if (t < Date.now()) { delete kv[x]; delete kv.__exp[x] }
    saveKv()
  },
  async delete(k) { delete kv[k]; if (kv.__exp) delete kv.__exp[k]; saveKv() },
  async list({ prefix = '' } = {}) { return { keys: Object.keys(kv).filter(k => k !== '__exp' && k.startsWith(prefix) && alive(k)).sort().map(name => ({ name })), list_complete: true } },
}

// Прокси для Telegram (TG_PROXY): запросы к api.telegram.org идут через HTTP-прокси (CONNECT), остальные — напрямую
const PROXY = (() => { try { const u = new URL((process.env.TG_PROXY || '').trim()); return /^https?:$/.test(u.protocol) ? u : null } catch { return null } })()
if (PROXY) {
  const direct = globalThis.fetch
  globalThis.fetch = async (input, init = {}) => {
    const url = new URL(typeof input === 'string' ? input : input.url)
    if (url.hostname !== 'api.telegram.org') return direct(input, init)
    const sock = await new Promise((ok, bad) => {
      const c = net.connect(+PROXY.port || 80, PROXY.hostname)
      const auth = PROXY.username ? 'Proxy-Authorization: Basic ' + Buffer.from(decodeURIComponent(PROXY.username) + ':' + decodeURIComponent(PROXY.password)).toString('base64') + '\r\n' : ''
      c.setTimeout(20000, () => c.destroy(new Error('прокси не отвечает')))
      c.once('error', bad)
      c.once('connect', () => c.write(`CONNECT api.telegram.org:443 HTTP/1.1\r\nHost: api.telegram.org:443\r\n${auth}\r\n`))
      c.once('data', d => { const line = d.toString().split('\r\n')[0]; if (/ 200 /.test(line)) { c.setTimeout(0); ok(c) } else { c.destroy(); bad(new Error('прокси: ' + line)) } })
    })
    const body = init.body == null ? null : Buffer.from(init.body)
    return new Promise((ok, bad) => {
      const req = https.request({ host: 'api.telegram.org', path: url.pathname + url.search, method: init.method || 'GET', headers: { ...(init.headers || {}), ...(body ? { 'content-length': body.length } : {}) }, socket: sock, servername: 'api.telegram.org', agent: false }, res => {
        const ch = []; res.on('data', c => ch.push(c)); res.on('end', () => ok(new Response(Buffer.concat(ch), { status: res.statusCode, headers: { 'content-type': res.headers['content-type'] || 'application/json' } })))
      })
      req.on('error', bad); if (init.signal) init.signal.addEventListener('abort', () => req.destroy(new Error('timeout')))
      if (body) req.write(body); req.end()
    })
  }
  console.log('Telegram — через прокси ' + PROXY.hostname + ':' + (PROXY.port || 80))
}

const api = async (method, body, ms = 60000) => {
  const r = await fetch(`https://api.telegram.org/bot${env.BOT_TOKEN}/${method}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body || {}), signal: AbortSignal.timeout(ms) })
  return r.json()
}
const sleep = ms => new Promise(r => setTimeout(r, ms))
// По желанию (меню apex → «Уведомления владельцу»): сообщение владельцу при запуске и остановке
const NOTIFY = process.env.NOTIFY === '1' && /^\d{4,15}$/.test(env.OWNER_ID)
const notify = text => NOTIFY ? api('sendMessage', { chat_id: env.OWNER_ID, text }, 10000).catch(() => {}) : Promise.resolve()
for (const sig of ['SIGTERM', 'SIGINT']) process.once(sig, async () => { await notify('⏸ Бот остановлен на сервере ' + os.hostname()); process.exit(0) })

// Передаём каждое обновление коду бота так же, как это делает Telegram в Cloudflare
const handle = u => worker.fetch(new Request('http://localhost/tg', {
  method: 'POST', headers: { 'content-type': 'application/json', 'x-telegram-bot-api-secret-token': env.WEBHOOK_SECRET }, body: JSON.stringify(u),
}), env)

async function main() {
  const me = await api('getMe', {}, 20000).catch(e => ({ ok: false, description: e.message }))
  if (!me.ok) { console.error('Telegram не принял токен: ' + me.description); process.exit(1) }
  const wh = await api('deleteWebhook', { drop_pending_updates: false }, 20000).catch(() => ({}))
  console.log(`Бот @${me.result.username} запущен на сервере${wh.ok ? ' (адрес Cloudflare отключён)' : ''}`)
  notify('🟢 Бот @' + me.result.username + ' запущен на сервере ' + os.hostname())
  let offset = 0, fails = 0
  for (;;) {
    try {
      const r = await api('getUpdates', { offset, timeout: 50, allowed_updates: ['message', 'callback_query'] }, 70000)
      if (!r.ok) {
        if (r.error_code === 409) console.error('Бот уже работает в другом месте (Cloudflare или второй сервер) — отключаю его адрес')
        if (r.error_code === 409) await api('deleteWebhook', {}, 20000).catch(() => {})
        throw new Error(r.description || 'getUpdates')
      }
      fails = 0
      for (const u of r.result) {
        offset = u.update_id + 1
        await handle(u).catch(e => console.error('Ошибка в обработке:', e.message))
      }
    } catch (e) {
      fails++
      console.error('Нет связи с Telegram:', e.message)
      await sleep(Math.min(60000, 2000 * fails))
    }
  }
}

// По желанию: адрес для итогов конкурсов от робота GitHub (BOT_WORKER_URL). Только /api/draw.
const port = parseInt(process.env.BOT_PORT || '', 10)
if (port > 0 && process.env.WEBHOOK_SECRET) {
  http.createServer(async (req, res) => {
    const chunks = []; for await (const c of req) { chunks.push(c); if (chunks.length > 64) break }
    if (!(req.url === '/api/draw' && req.method === 'POST')) { res.writeHead(404).end(); return }
    const r = await worker.fetch(new Request('http://localhost/api/draw', { method: 'POST', headers: { 'x-key': String(req.headers['x-key'] || ''), 'content-type': 'application/json' }, body: Buffer.concat(chunks) }), env)
    res.writeHead(r.status, { 'content-type': 'application/json' }).end(await r.text())
  }).listen(port, process.env.BOT_HOST || '127.0.0.1', () => console.log('Итоги конкурсов: порт ' + port))
}

main()
