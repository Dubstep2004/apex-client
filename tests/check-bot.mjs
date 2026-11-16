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
  msg('вопрос 1'), msg('вопрос 2'), msg('вопрос 3'), msg('вопрос 4'), msg('/stats', { id: 1 }), msg('/stats'), msg('<b>x</b>', { id: 9, first_name: '<i>' })]
globalThis.fetch = async (url, init = {}) => {
  url = String(url); const ok = r => new Response(JSON.stringify({ ok: true, result: r }))
  if (url.includes('config/app.json')) return new Response(JSON.stringify({ servers: { status: { 'Германия': { state: 'maintenance', until: Date.now() + 3600e3 } } } }))
  if (url.includes('tg/bot.json')) return new Response(JSON.stringify({ features: { forward: true } }))
  if (!url.includes('api.telegram.org')) return new Response('{}', { status: 404 })
  const m = url.split('/').pop(), b = JSON.parse(init.body || '{}')
  if (m === 'getMe') return ok({ username: 'test_bot' })
  if (m === 'getUpdates') { n++
    if (n === 1) return ok(U.map((message, i) => ({ update_id: i + 1, message })))
    if (n === 2) return ok([{ update_id: 99, message: msg('Ответ', { id: 1 }, { reply_to_message: { message_id: 999 } }) }, { update_id: 100, message: msg('чужой ответ', { id: 5 }, { reply_to_message: { message_id: 999 } }) }])
    process.stdout.write('SENT' + JSON.stringify(sent) + '\\n'); process.exit(0) }
  sent.push([m, b.chat_id, b.text || '', b.parse_mode || '', b.from_chat_id])
  return ok(m === 'forwardMessage' ? { message_id: 999 } : {})
}`)
const r = spawnSync(process.execPath, ['--import', mock, path.join(BOT, 'server.js')], { env: { ...process.env, BOT_TOKEN: '123456:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', OWNER_ID: '1', BOT_DATA: path.join(tmp, 'data') }, encoding: 'utf8', timeout: 30000 })
const line = (r.stdout || '').split('\n').find(l => l.startsWith('SENT'))
if (!line) fail('бот не ответил: ' + (r.stderr || r.stdout).slice(0, 300))
else {
  const sent = JSON.parse(line.slice(4)), to = id => sent.filter(s => s[1] === id && s[0] === 'sendMessage').map(s => s[2])
  const has = (id, re, what) => { if (!to(id).some(t => re.test(t))) fail('бот: ' + what) }
  has(7, /Привет/, '/start без приветствия')
  has(7, /Германия — техработы/, '/status не показал техработы')
  has(1, /Работаю/, '/ping владельцу не ответил')
  if (to(7).some(t => /Работаю/.test(t))) fail('бот: /ping ответил не владельцу')
  has(8, /does not connect/i, 'нет ответа на английском')
  has(7, /Как установить/, 'поиск по словам не нашёл тему')
  const fw = sent.filter(s => s[0] === 'forwardMessage').length
  if (fw !== 5) fail('пересылка владельцу: ожидалось 5 (3 от одного человека — дальше лимит — и по 1 от двух других), было ' + fw)
  has(1, /Вопросы за 7 дней/, '/stats владельцу не ответил')
  if (to(7).some(t => /Вопросы за 7 дней/.test(t))) fail('бот: /stats ответил не владельцу')
  if (sent.filter(s => s[0] === 'copyMessage').length !== 1) fail('ответ владельца: должен уйти один раз и только от владельца')
  if (sent.some(s => s[3])) fail('бот отправил сообщение с разметкой (parse_mode) — имена людей могли бы сломать текст')
  const kv = JSON.parse(fs.readFileSync(path.join(tmp, 'data/kv.json'), 'utf8'))
  if (!kv.__exp || !Object.keys(kv).some(k => k.startsWith('st:'))) fail('счёт тем не записан или без срока хранения')
}
if (/\d{6,}:[A-Za-z0-9_-]{30,}/.test((r.stdout || '') + (r.stderr || ''))) fail('токен бота попал в журнал')

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
const ws = fs.readFileSync(path.join(BOT, 'worker.js'), 'utf8')
if (!/x-telegram-bot-api-secret-token/.test(ws) || !/WEBHOOK_SECRET/.test(ws)) fail('worker.js: нет проверки секретного слова')
if ((ws.match(/^\/\/ V\|/m) || []).length === 0) fail('worker.js: нет «Что нового в боте»')

fs.rmSync(tmp, { recursive: true, force: true })
console.log(errs.length ? `✗ Бот и меню: ошибок ${errs.length}` : '✓ Бот и меню — без ошибок')
process.exit(errs.length ? 1 : 0)
