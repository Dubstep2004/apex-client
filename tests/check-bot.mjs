// Проверка бота и меню сервера (tools/tg-bot) без настоящего Telegram: node tests/check-bot.mjs
// Бот: подменяем Telegram и сайт, гоняем сообщения через server.js → worker.js и сверяем ответы.
// Меню: синтаксис bash, токен не попадает в командную строку, восстановление не принимает чужие архивы.
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..'), BOT = path.join(ROOT, 'tools/tg-bot')
const errs = [], fail = m => { errs.push(m); console.log('::error::' + m) }
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'apexbot-'))

// ── 1. Бот: подменённый Telegram
const mock = path.join(tmp, 'mock.mjs')
fs.writeFileSync(mock, `
let n = 0, mid = 100; const sent = []
const msg = (text, from = { id: 7, first_name: 'Ivan', language_code: 'ru' }, x = {}) => ({ chat: { id: from.id, type: 'private' }, from, text, message_id: ++mid, ...x })
const U = [msg('/start'), msg('/status'), msg('/ping', { id: 1 }), msg('/ping'), msg('it does not connect', { id: 8, language_code: 'en' }), msg('как установить'),
  msg('вопрос 1'), msg('вопрос 2'), msg('вопрос 3'), msg('вопрос 4'), msg('/stats', { id: 1 }), msg('/stats'), msg('<b>x</b>', { id: 9, first_name: '<i>' }),
  msg('/start ref_23', { id: 20 }), msg('/start install', { id: 21 }), msg('/invite', { id: 23 }), msg('у меня версия 3.20', { id: 22 }), msg('/stop', { id: 21 }),
  msg('не работает Германия', { id: 30 }), msg('не работает Германия', { id: 31 }), msg('не работает Германия', { id: 32 }),
  ...Array.from({ length: 10 }, (_, i) => msg('флуд ' + i, { id: 40 })),
  { chat: { id: -100, type: 'supergroup' }, from: { id: 50 }, message_id: 500, new_chat_members: [{ id: 50, first_name: 'Нов' }] },
  { chat: { id: -100, type: 'supergroup' }, from: { id: 50 }, message_id: 501, text: 'купи https://spam.example', entities: [{ type: 'url', offset: 5, length: 20 }] },
  { chat: { id: -100, type: 'supergroup' }, from: { id: 51 }, message_id: 502, text: 'https://ok.example', entities: [{ type: 'url', offset: 0, length: 18 }] }]
globalThis.fetch = async (url, init = {}) => {
  url = String(url); const ok = r => new Response(JSON.stringify({ ok: true, result: r }))
  if (url.includes('config/app.json')) return new Response(JSON.stringify({ servers: { status: { 'Германия': { state: 'maintenance', until: Date.now() + 3600e3 } } } }))
  if (url.includes('tg/bot.json')) return new Response(JSON.stringify({ features: { forward: true }, broadcast: { id: 'bc-test-1', text: 'Привет всем' }, description: 'Описание' }))
  if (url.includes('releases/latest')) return new Response(JSON.stringify({ tag_name: 'v3.22.0' }))
  if (!url.includes('api.telegram.org')) return new Response('{}', { status: 404 })
  const m = url.split('/').pop(), b = JSON.parse(init.body || '{}')
  if (m === 'getMe') return ok({ username: 'test_bot' })
  if (m === 'getUpdates') { n++
    if (n === 1) return ok(U.map((message, i) => ({ update_id: i + 1, message })))
    if (n === 2) return ok([{ update_id: 99, message: msg('Ответ владельца', { id: 1 }, { reply_to_message: { message_id: 1000 } }) }, { update_id: 100, message: msg('чужой ответ', { id: 5 }, { reply_to_message: { message_id: 1000 } }) },
      { update_id: 101, callback_query: { id: 'c1', from: { id: 1 }, data: 'qr:0:1000', message: { chat: { id: 1 }, message_id: 7 } } }, { update_id: 102, callback_query: { id: 'c2', from: { id: 5 }, data: 'qr:1:1000', message: { chat: { id: 5 }, message_id: 8 } } },
      { update_id: 103, message: msg('/settings', { id: 1 }) }, { update_id: 104, callback_query: { id: 'c3', from: { id: 5 }, data: 'ft:status', message: { chat: { id: 5 }, message_id: 9 } } }, { update_id: 105, callback_query: { id: 'c4', from: { id: 1 }, data: 'ft:status', message: { chat: { id: 1 }, message_id: 10 } } }, { update_id: 106, message: msg('/status', { id: 60 }) }, { update_id: 107, message: msg('/settings', { id: 70 }) }, { update_id: 108, message: msg('/stats', { id: 71 }) }, { update_id: 109, message: msg('/ping', { id: 71 }) }, { update_id: 110, message: msg('/contests', { id: 71 }) }, { update_id: 111, message: msg('/draw 1', { id: 71 }) }, { update_id: 112, callback_query: { id: 'c9', from: { id: 77 }, data: 'ft:news', message: { chat: { id: 77 }, message_id: 11 } } }])
    if (n < 6) { await new Promise(r => setTimeout(r, 1500)); return ok([]) }
    process.stdout.write('SENT' + JSON.stringify(sent) + '\\n'); process.exit(0) }
  sent.push([m, b.chat_id, b.text || '', b.parse_mode || '', b.from_chat_id, b.callback_query_id === 'c9' ? 77 : 0])
  return ok(m === 'forwardMessage' ? { message_id: 1000 + sent.filter(x => x[0] === 'forwardMessage').length - 1 } : {})
}`)
const r = spawnSync(process.execPath, ['--import', mock, path.join(BOT, 'server.js')], { env: { ...process.env, BOT_TOKEN: '123456:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', OWNER_ID: '1', BOT_DATA: path.join(tmp, 'data') }, encoding: 'utf8', timeout: 30000 })
const line = (r.stdout || '').split('\n').find(l => l.startsWith('SENT'))
if (!line) fail('бот не ответил: ' + (r.stderr || r.stdout).slice(0, 300))
else {
  const sent = JSON.parse(line.slice(4)); if (process.env.DBG) for (const x of sent) console.log(JSON.stringify(x).slice(0, 170))
  const to = id => sent.filter(s => String(s[1]) === String(id) && s[0] === 'sendMessage').map(s => s[2])
  const has = (id, re, what) => { if (!to(id).some(t => re.test(t))) fail('бот: ' + what) }
  has(7, /Привет/, '/start без приветствия')
  has(7, /Германия — техработы/, '/status не показал техработы')
  has(1, /Работаю/, '/ping владельцу не ответил')
  if (to(7).some(t => /Работаю/.test(t))) fail('бот: /ping ответил не владельцу')
  has(8, /does not connect/i, 'нет ответа на английском')
  has(7, /Как установить/, 'поиск по словам не нашёл тему')
  const fw = sent.filter(s => s[0] === 'forwardMessage').length
  if (fw !== 8) fail('пересылка владельцу: ожидалось 8 (по 3 от двух людей — дальше лимит — и по 1 от двух других), было ' + fw)
  has(1, /Вопросы за 7 дней/, '/stats владельцу не ответил')
  if (to(7).some(t => /Вопросы за 7 дней/.test(t))) fail('бот: /stats ответил не владельцу')
  if (!to(7).includes('Ответ владельца')) fail('ответ владельца не дошёл до человека')
  if (to(7).includes('чужой ответ') || to(9).includes('чужой ответ')) fail('ответ НЕ владельца ушёл человеку')
  if (to(7).filter(t => /Обновите подписку/.test(t)).length !== 1) fail('быстрый ответ: должен уйти один раз и только по кнопке владельца')
  has(21, /Как установить APEX CLIENT/, 'ссылка ?start=install не открыла тему')
  has(23, /start=ref_23[\s\S]*Приглашено: 1/, '/invite: нет ссылки или не засчитано приглашение')
  has(22, /Есть версия новее: 3\.22\.0/, '«у меня 3.20» — не подсказал новую версию')
  has(1, /Жалобы на сервер «Германия»: 3/, 'жалобы на сервер не дошли владельцу')
  const fl = to(40); if (fl.length > 9 || !fl.some(t => /Слишком часто/.test(t))) fail('защита от флуда не сработала: ответов ' + fl.length)
  has(-100, /Добро пожаловать, Нов/, 'группа: нет приветствия новичка')
  const del = sent.filter(s => s[0] === 'deleteMessage'); if (del.length !== 1) fail('группа: ссылку от новичка надо удалить, старожила — нет; удалено ' + del.length)
  const bc = sent.filter(s => s[0] === 'sendMessage' && /^Привет всем/.test(s[2])).map(s => s[1])
  if (!bc.length || bc.includes(21) || bc.includes(1)) fail('рассылка: не дошла, или пришла тому, кто нажал /stop, или владельцу: ' + JSON.stringify(bc))
  if (!sent.some(s => s[0] === 'setMyCommands')) fail('меню команд «/» не выставлено')
  has(1, /Функции бота — нажмите/, '/settings владельцу не ответил')
  // Команды владельца у других: не работают и ничего не выдают
  for (const c of ['/settings', '/stats', '/ping', '/contests', '/draw 1', '/help']) if (to(70).concat(to(71)).some(t => /Функции бота|Вопросы за 7|Работаю|Команды владельца|Конкурс|уч\./.test(t))) fail('команда владельца сработала у другого человека: ' + c)
  if (!to(70).some(t => /Такой команды нет/.test(t))) fail('чужой /settings должен получить «такой команды нет»')
  if (sent.some(s => s[0] === 'answerCallbackQuery' && /Отправлено|включено|выключено/.test(s[2]) && s[5] === 77)) fail('кнопка владельца ответила другому человеку')
  { const ov = JSON.parse(JSON.parse(fs.readFileSync(path.join(tmp, 'data/kv.json'), 'utf8')).fover || '{}'); if (ov.news) fail('чужое нажатие кнопки /settings переключило функцию') }
  if (!sent.some(s => s[0] === 'answerCallbackQuery' && s[5] === 77 && s[2] === '')) fail('чужая кнопка владельца: нет тихого ответа (у человека будет крутиться загрузка)')
  if (to(60).some(t => /Состояние серверов|Все серверы/.test(t))) fail('/settings: выключили /status кнопкой, а он всё равно отвечает')
  if (sent.filter(s => s[0] === 'editMessageReplyMarkup' && String(s[1]) === '5').length) fail('/settings: кнопки сработали не у владельца')
  const kvj = JSON.parse(fs.readFileSync(path.join(tmp, 'data/kv.json'), 'utf8')); if (!JSON.parse(kvj.fover || '{}').status || JSON.parse(kvj.fover).status.v !== false) fail('/settings: переключение не сохранилось')
  if (sent.some(s => s[3])) fail('бот отправил сообщение с разметкой (parse_mode) — имена людей могли бы сломать текст')
  const kv = JSON.parse(fs.readFileSync(path.join(tmp, 'data/kv.json'), 'utf8'))
  if (!kv.__exp || !Object.keys(kv).some(k => k.startsWith('st:'))) fail('счёт тем не записан или без срока хранения')
}
if (/\d{6,}:[A-Za-z0-9_-]{30,}/.test((r.stdout || '') + (r.stderr || ''))) fail('токен бота попал в журнал')

// ── 1b. Связь с пультом: /api/data только с верной подписью, ответ зашифрован словом
{
  const { spawn } = await import('node:child_process'), crypto = await import('node:crypto')
  const m3 = path.join(tmp, 'mock3.mjs'), d3 = path.join(tmp, 'd3'); fs.mkdirSync(d3)
  fs.writeFileSync(path.join(d3, 'kv.json'), JSON.stringify({ q: JSON.stringify([{ id: 5, uid: 7, text: 'помогите', at: 1 }]), users: '[7,8]' }))
  fs.writeFileSync(m3, `globalThis.fetch = async (url) => { url = String(url); const ok = r => new Response(JSON.stringify({ ok: true, result: r }))
    if (!url.includes('api.telegram.org')) return new Response('{}', { status: 404 })
    const m = url.split('/').pop(); if (m === 'getMe') return ok({ username: 'b' }); if (m === 'getUpdates') { await new Promise(r => setTimeout(r, 1000)); return ok([]) } return ok({}) }`)
  const port = 18000 + Math.floor(Math.random() * 1000), key = 'secretword123456'
  const srv = spawn(process.execPath, ['--import', m3, path.join(BOT, 'server.js')], { env: { ...process.env, BOT_TOKEN: '123456:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', BOT_DATA: d3, WEBHOOK_SECRET: key, BOT_PORT: String(port), BOT_HOST: '127.0.0.1' } })
  await new Promise(r => setTimeout(r, 2500))
  const call = (k, p = '/api/data') => { const ts = String(Date.now()); return fetch(`http://127.0.0.1:${port}${p}`, { method: 'POST', headers: { 'x-ts': ts, 'x-sig': crypto.createHmac('sha256', k).update(ts).digest('hex') }, body: '{}' }) }
  try {
    const bad = await call('wrong-word-000000'); if (bad.status !== 403) fail('связь с пультом: чужая подпись принята (' + bad.status + ')')
    const nosig = await fetch(`http://127.0.0.1:${port}/api/data`, { method: 'POST', body: '{}' }); if (nosig.status !== 403) fail('связь с пультом: запрос без подписи принят')
    const other = await fetch(`http://127.0.0.1:${port}/tg`, { method: 'POST', body: '{}' }); if (other.status !== 404) fail('связь с пультом: открыт лишний адрес /tg')
    const box = await (await call(key)).json()
    const k = crypto.createHash('sha256').update('apex-bot:' + key).digest(), raw = Buffer.from(box.c, 'base64')
    const dc = crypto.createDecipheriv('aes-256-gcm', k, Buffer.from(box.i, 'base64')); dc.setAuthTag(raw.subarray(-16))
    const data = JSON.parse(Buffer.concat([dc.update(raw.subarray(0, -16)), dc.final()]).toString())
    if (data.users !== 2 || data.q?.[0]?.text !== 'помогите') fail('связь с пультом: неверные данные ' + JSON.stringify(data).slice(0, 120))
    if (JSON.stringify(box).includes('помогите')) fail('связь с пультом: данные не зашифрованы')
  } catch (e) { fail('связь с пультом: ' + e.message) } finally { srv.kill() }
}

// ── 2. Меню сервера
const sh = path.join(BOT, 'apex.sh'), src = fs.readFileSync(sh, 'utf8')
if (spawnSync('bash', ['-n', sh]).status !== 0) fail('apex.sh: ошибка синтаксиса bash')
src.split('\n').forEach((l, i) => {
  if (/curl[^|]*api\.telegram\.org\/bot\$/.test(l) && !/curlk/.test(l)) fail(`apex.sh:${i + 1}: токен в командной строке curl — используйте curlk`)
  if (/\bcurl\b[^|]*-x\s+"\$/.test(l)) fail(`apex.sh:${i + 1}: прокси с паролем в командной строке curl — используйте curlk`)
  if (/set -x|printenv|echo[^#]*\$t\b|echo[^#]*BOT_TOKEN/.test(l)) fail(`apex.sh:${i + 1}: может напечатать токен`)
})
const ver = (src.match(/^APEX_VER="(.+?)"/m) || [])[1], top = (src.match(/^CHANGES='V\|([^|]+)\|/m) || [])[1]
if (!ver || ver !== top) fail(`apex.sh: версия меню ${ver} и первая запись «Что нового» ${top} не совпадают`)
for (const fn of ['do_install', 'do_update', 'do_uninstall', 'do_remove_menu', 'restore_file', 'do_backup', 'do_news', 'tg_check']) if (!new RegExp('^' + fn + '\\(\\)', 'm').test(src)) fail('apex.sh: нет ' + fn)
// Восстановление отказывается от архива со ссылкой вместо файла
const ar = path.join(tmp, 'evil'); fs.mkdirSync(path.join(ar, 'etc'), { recursive: true }); fs.symlinkSync('/tmp/nowhere', path.join(ar, 'etc/apex-bot.env'))
spawnSync('tar', ['-czf', path.join(tmp, 'evil.tgz'), '-C', ar, 'etc/apex-bot.env'])
const rs = spawnSync('bash', ['-c', `source <(sed -n '/^say()/,/^need_root/p;/^restore_file()/,/^}/p;/^envget()/p;/^installed()/p' "${sh}"); DIR=/nonexistent ENVF=/etc/apex-bot.env DATA=${tmp}/d; restore_file ${tmp}/evil.tgz; echo rc=$?`], { encoding: 'utf8' })
if (!/rc=1/.test(rs.stdout) || !/ссылки/.test(rs.stdout)) fail('apex.sh: восстановление приняло архив со ссылкой: ' + (rs.stdout + rs.stderr).slice(0, 200))
// Функции бота в меню: список читается из worker.js, переключение из бота видно
{
  const d = path.join(tmp, 'bf'); fs.mkdirSync(d); fs.writeFileSync(path.join(d, 'kv.json'), JSON.stringify({ fover: JSON.stringify({ status: { v: false, at: 9e12 } }) }))
  const r = spawnSync('bash', ['-c', `source <(sed -n '/^botfeat_list()/,/^}/p' "${sh}"); DIR="${BOT}" DATA="${d}"; botfeat_list`], { encoding: 'utf8', timeout: 30000 })
  const rows = (r.stdout || '').trim().split('\n').filter(Boolean)
  if (rows.length < 25) fail('меню → «Функции бота»: прочитано функций ' + rows.length + ' ' + (r.stderr || '').slice(0, 150))
  if (!rows.some(x => /\|status\|.*\|0$/.test(x))) fail('меню → «Функции бота»: не видно, что /status выключен в боте')
}
const ws = fs.readFileSync(path.join(BOT, 'worker.js'), 'utf8')
if (!/x-telegram-bot-api-secret-token/.test(ws) || !/WEBHOOK_SECRET/.test(ws)) fail('worker.js: нет проверки секретного слова')
if ((ws.match(/^\/\/ V\|/m) || []).length === 0) fail('worker.js: нет «Что нового в боте»')
if (!ws.includes("async scheduled(")) fail('worker.js: нет расписания для Cloudflare (scheduled) — рассылка встанет, если боту не пишут')

fs.rmSync(tmp, { recursive: true, force: true })
console.log(errs.length ? `✗ Бот и меню: ошибок ${errs.length}` : '✓ Бот и меню — без ошибок')
process.exit(errs.length ? 1 : 0)
