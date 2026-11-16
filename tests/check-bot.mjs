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
  if (url.includes('tg/bot.json')) return new Response(JSON.stringify({ channel: '@apextestch', features: { forward: true, captcha: true }, broadcast: { id: 'bc-test-1', text: 'Привет всем' }, description: 'Описание' }))
  if (url.includes('releases/latest')) return new Response(JSON.stringify({ tag_name: 'v3.22.0' }))
  if (url.includes('panel.test/api/users/by-telegram-id/')) return new Response(JSON.stringify({ response: [{ uuid: 'u-1', status: 'ACTIVE', expireAt: new Date(Date.now() + 5 * 864e5).toISOString(), usedTrafficBytes: 5368709120, trafficLimitBytes: 0, subscriptionUrl: 'https://secret.example/sub/XYZ' }] }))
  if (url === 'https://panel.test/api/users' && init.method === 'PATCH') { sent.push(['remnaPatch', 0, init.body || '']); return new Response(JSON.stringify({ response: {} })) }
  if (!url.includes('api.telegram.org')) return new Response('{}', { status: 404 })
  const m = url.split('/').pop(), b = m === 'sendDocument' ? { chat_id: 1, text: 'FILE ' + new TextDecoder().decode(init.body) } : JSON.parse(init.body || '{}')
  if (m === 'getMe') return ok({ username: 'test_bot' })
  if (m === 'getUpdates') { n++
    if (n === 1) return ok(U.map((message, i) => ({ update_id: i + 1, message })))
    if (n === 2) return ok([{ update_id: 99, message: msg('Ответ владельца', { id: 1 }, { reply_to_message: { message_id: 1000 } }) }, { update_id: 100, message: msg('чужой ответ', { id: 5 }, { reply_to_message: { message_id: 1000 } }) },
      { update_id: 101, callback_query: { id: 'c1', from: { id: 1 }, data: 'qr:0:1000', message: { chat: { id: 1 }, message_id: 7 } } }, { update_id: 102, callback_query: { id: 'c2', from: { id: 5 }, data: 'qr:1:1000', message: { chat: { id: 5 }, message_id: 8 } } },
      { update_id: 103, message: msg('/settings', { id: 1 }) }, { update_id: 104, callback_query: { id: 'c3', from: { id: 5 }, data: 'ft:status', message: { chat: { id: 5 }, message_id: 9 } } }, { update_id: 105, callback_query: { id: 'c4', from: { id: 1 }, data: 'ft:status', message: { chat: { id: 1 }, message_id: 10 } } }, { update_id: 106, message: msg('/status', { id: 60 }) }, { update_id: 107, message: msg('/settings', { id: 70 }) }, { update_id: 108, message: msg('/stats', { id: 71 }) }, { update_id: 109, message: msg('/ping', { id: 71 }) }, { update_id: 110, message: msg('/contests', { id: 71 }) }, { update_id: 111, message: msg('/draw 1', { id: 71 }) }, { update_id: 112, callback_query: { id: 'c9', from: { id: 77 }, data: 'ft:news', message: { chat: { id: 77 }, message_id: 11 } } }])
    if (n === 3) return ok([{ update_id: 200, message: msg('/start', { id: 1 }) }, { update_id: 201, callback_query: { id: 'a1', from: { id: 1 }, data: 'ad:today', message: { chat: { id: 1 }, message_id: 50 } } },
      { update_id: 202, callback_query: { id: 'a2', from: { id: 1 }, data: 'ad:q', message: { chat: { id: 1 }, message_id: 50 } } }, { update_id: 203, callback_query: { id: 'a3', from: { id: 1 }, data: 'ad:qa:1003', message: { chat: { id: 1 }, message_id: 50 } } },
      { update_id: 204, message: msg('Ответ из панели', { id: 1 }) }, { update_id: 205, callback_query: { id: 'a4', from: { id: 1 }, data: 'ad:bc', message: { chat: { id: 1 }, message_id: 50 } } },
      { update_id: 206, message: msg('Рассылка из бота', { id: 1 }) }, { update_id: 207, callback_query: { id: 'a5', from: { id: 1 }, data: 'ad:bcok', message: { chat: { id: 1 }, message_id: 51 } } },
      { update_id: 208, callback_query: { id: 'a6', from: { id: 88 }, data: 'ad:today', message: { chat: { id: 88 }, message_id: 52 } } }, { update_id: 209, message: msg('🛠 Админ', { id: 88 }) }, { update_id: 210, callback_query: { id: 'u1', from: { id: 1 }, data: 'ad:users', message: { chat: { id: 1 }, message_id: 60 } } }, { update_id: 211, callback_query: { id: 'u2', from: { id: 1 }, data: 'ad:uc:20', message: { chat: { id: 1 }, message_id: 60 } } }, { update_id: 212, callback_query: { id: 'u3', from: { id: 1 }, data: 'ad:ub2:20', message: { chat: { id: 1 }, message_id: 60 } } }, { update_id: 213, callback_query: { id: 'u4', from: { id: 88 }, data: 'ad:ub2:7', message: { chat: { id: 88 }, message_id: 61 } } }, { update_id: 214, message: msg('/today', { id: 1 }) }, { update_id: 215, message: msg('/queue', { id: 1 }) }, { update_id: 216, message: msg('/top', { id: 1 }) }, { update_id: 217, message: msg('/today', { id: 72 }) }, { update_id: 218, callback_query: { id: 'm1', from: { id: 1 }, data: 'ad:mt:1000', message: { chat: { id: 1 }, message_id: 62 } } }, { update_id: 219, callback_query: { id: 'm2', from: { id: 1 }, data: 'ad:kt', message: { chat: { id: 1 }, message_id: 62 } } }, { update_id: 220, callback_query: { id: 'p1', from: { id: 1 }, data: 'ad:poll', message: { chat: { id: 1 }, message_id: 63 } } }, { update_id: 221, message: msg('Как работает?\\nОтлично\\nПлохо', { id: 1 }) }, { update_id: 222, callback_query: { id: 'p2', from: { id: 1 }, data: 'ad:pok', message: { chat: { id: 1 }, message_id: 64 } } }, { update_id: 223, callback_query: { id: 'p3', from: { id: 1 }, data: 'ad:pres', message: { chat: { id: 1 }, message_id: 64 } } }, { update_id: 224, callback_query: { id: 'p4', from: { id: 88 }, data: 'ad:poll', message: { chat: { id: 88 }, message_id: 65 } } }, { update_id: 225, callback_query: { id: 'e1', from: { id: 1 }, data: 'ad:qe:0_1000', message: { chat: { id: 1 }, message_id: 66 } } }, { update_id: 226, message: msg('❌ Отмена', { id: 1 }) }, { update_id: 227, callback_query: { id: 'k1', from: { id: 1 }, data: 'ad:post', message: { chat: { id: 1 }, message_id: 67 } } }, { update_id: 228, message: msg('Новость для канала', { id: 1 }) }, { update_id: 229, callback_query: { id: 'k2', from: { id: 1 }, data: 'ad:postok', message: { chat: { id: 1 }, message_id: 67 } } }, { update_id: 230, callback_query: { id: 'k3', from: { id: 1 }, data: 'ad:cnew', message: { chat: { id: 1 }, message_id: 67 } } }, { update_id: 231, message: msg('Разыгрываем подписку', { id: 1 }) }, { update_id: 232, callback_query: { id: 'k4', from: { id: 1 }, data: 'ad:cgo:1_1', message: { chat: { id: 1 }, message_id: 67 } } }, { update_id: 233, callback_query: { id: 'k5', from: { id: 1 }, data: 'ad:backup', message: { chat: { id: 1 }, message_id: 67 } } }, { update_id: 234, callback_query: { id: 'k6', from: { id: 1 }, data: 'ad:sys', message: { chat: { id: 1 }, message_id: 67 } } }, { update_id: 235, callback_query: { id: 'k7', from: { id: 88 }, data: 'ad:post', message: { chat: { id: 88 }, message_id: 67 } } }, { update_id: 236, callback_query: { id: 'k8', from: { id: 88 }, data: 'ad:backup', message: { chat: { id: 88 }, message_id: 67 } } }, { update_id: 237, callback_query: { id: 'k9', from: { id: 1 }, data: 'ad:rst', message: { chat: { id: 1 }, message_id: 67 } } }, { update_id: 238, callback_query: { id: 'h0', from: { id: 1, username: 'hlp' }, data: 'ad:hmk:23', message: { chat: { id: 1 }, message_id: 68 } } }, { update_id: 239, callback_query: { id: 'h1', from: { id: 23, username: 'hlp' }, data: 'ad:q', message: { chat: { id: 23 }, message_id: 68 } } }, { update_id: 240, callback_query: { id: 'h2', from: { id: 23, username: 'hlp' }, data: 'ad:qa:1007', message: { chat: { id: 23 }, message_id: 68 } } }, { update_id: 241, message: msg('Ответ помощника', { id: 23, username: 'hlp' }) }, { update_id: 242, callback_query: { id: 'h3', from: { id: 23, username: 'hlp' }, data: 'ad:bc', message: { chat: { id: 23 }, message_id: 68 } } }, { update_id: 243, callback_query: { id: 'h4', from: { id: 23, username: 'hlp' }, data: 'ad:ub2:7', message: { chat: { id: 23 }, message_id: 68 } } }, { update_id: 244, callback_query: { id: 'h5', from: { id: 23, username: 'hlp' }, data: 'ad:feat', message: { chat: { id: 23 }, message_id: 68 } } }, { update_id: 245, callback_query: { id: 'h6', from: { id: 1, username: 'hlp' }, data: 'ad:helpers', message: { chat: { id: 1 }, message_id: 68 } } }, { update_id: 246, callback_query: { id: 'h7', from: { id: 22, username: 'hlp' }, data: 'ad:q', message: { chat: { id: 22 }, message_id: 68 } } }, { update_id: 247, message: msg('🛟 Помощник', { id: 23, username: 'hlp' }) }, { update_id: 248, message: msg('/start', { id: 61 }) }, { update_id: 249, callback_query: { id: 'hm1', from: { id: 62 }, data: 'human', message: { chat: { id: 62 }, message_id: 70 } } }, { update_id: 250, message: msg('как установить', { id: 62 }) }, { update_id: 251, callback_query: { id: 'cp1', from: { id: 77 }, data: 'cap:50', message: { chat: { id: -200 }, message_id: 80 } } }, { update_id: 252, callback_query: { id: 'cp2', from: { id: 50 }, data: 'cap:50', message: { chat: { id: -200 }, message_id: 80 } } }, { update_id: 253, message: { chat: { id: -100, type: 'supergroup' }, from: { id: 1, first_name: 'G1' }, message_id: 853, text: '/warn', reply_to_message: { message_id: 502, from: { id: 51, first_name: 'Спамер' } } } }, { update_id: 254, message: { chat: { id: -100, type: 'supergroup' }, from: { id: 52, first_name: 'G52' }, message_id: 854, text: '/ban', reply_to_message: { message_id: 502, from: { id: 51, first_name: 'Спамер' } } } }, { update_id: 255, message: msg('как устоновить', { id: 63 }) }, { update_id: 256, message: msg('/report', { id: 64 }) }, { update_id: 257, message: msg('Samsung A52', { id: 64 }) }, { update_id: 258, message: msg('не знаю', { id: 64 }) }, { update_id: 259, message: msg('ничего не грузит', { id: 64 }) }, { update_id: 260, message: msg('/quiz', { id: 65 }) }, { update_id: 261, callback_query: { id: 'z1', from: { id: 65 }, data: 'qz:0:0:0', message: { chat: { id: 65 }, message_id: 90 } } }, { update_id: 262, callback_query: { id: 'z2', from: { id: 65 }, data: 'qz:4:0:4', message: { chat: { id: 65 }, message_id: 90 } } }, { update_id: 263, message: msg('как установить', { id: 66 }) }, { update_id: 264, message: msg('не помогло', { id: 66 }) }, { update_id: 265, callback_query: { id: 'fs1', from: { id: 1 }, data: 'fs:cmd', message: { chat: { id: 1 }, message_id: 91 } } }, { update_id: 266, callback_query: { id: 'fs2', from: { id: 67 }, data: 'fs:cmd', message: { chat: { id: 67 }, message_id: 92 } } }, { update_id: 267, callback_query: { id: 'sc1', from: { id: 1 }, data: 'ad:sec:ppl', message: { chat: { id: 1 }, message_id: 93 } } }, { update_id: 268, callback_query: { id: 'sc2', from: { id: 68 }, data: 'ad:sec:ppl', message: { chat: { id: 68 }, message_id: 94 } } }, { update_id: 269, message: msg('/video', { id: 69 }) }, { update_id: 270, message: msg('/sub', { id: 69 }) }, { update_id: 271, callback_query: { id: 'vd', from: { id: 70 }, data: 'vid', message: { chat: { id: 70 }, message_id: 95 } } }, { update_id: 272, callback_query: { id: 'rb', from: { id: 71 }, data: 'ad:rb:23', message: { chat: { id: 71 }, message_id: 96 } } }, { update_id: 273, callback_query: { id: 'rb2', from: { id: 1 }, data: 'ad:rb:23', message: { chat: { id: 1 }, message_id: 97 } } }])
    if (n < 7) { await new Promise(r => setTimeout(r, 1500)); return ok([]) }
    process.stdout.write('SENT' + JSON.stringify(sent) + '\\n'); process.exit(0) }
  sent.push([m, b.chat_id, b.text || '', b.parse_mode || '', b.from_chat_id, b.callback_query_id === 'c9' ? 77 : 0])
  return ok(m === 'forwardMessage' ? { message_id: 1000 + sent.filter(x => x[0] === 'forwardMessage').length - 1 } : {})
}`)
fs.mkdirSync(path.join(tmp, 'data'), { recursive: true }); fs.writeFileSync(path.join(tmp, 'data/kv.json'), JSON.stringify({ cauto: JSON.stringify([{ cid: 'bt1', at: 1, n: 1, mid: 5 }]), 'c:bt1': JSON.stringify({ ids: ['555'], users: { 555: { name: 'Победитель', username: 'winx' } } }) })) // конкурс, у которого вышел срок
const r = spawnSync(process.execPath, ['--import', mock, path.join(BOT, 'server.js')], { env: { ...process.env, BOT_TOKEN: '123456:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', OWNER_ID: '1', BOT_DATA: path.join(tmp, 'data'), REMNA_URL: 'https://panel.test', REMNA_TOKEN: 'test-remna-token' }, encoding: 'utf8', timeout: 30000 })
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
  const fw = sent.filter(s => s[0] === 'forwardMessage' && ![62, 64, 66].includes(s[4])).length // 62 — «Позвать человека», проверяется ниже
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
  const fl = to(40).filter(t => !/^(Привет всем|Рассылка из бота)/.test(t)); if (fl.length > 9 || !fl.some(t => /Слишком часто/.test(t))) fail('защита от флуда не сработала: ответов ' + fl.length)
  has(-100, /Добро пожаловать, Нов/, 'группа: нет приветствия новичка')
  const del = sent.filter(s => s[0] === 'deleteMessage' && s[1] === -100); if (del.length !== 1) fail('группа: ссылку от новичка надо удалить, старожила — нет; удалено ' + del.length)
  const bc = sent.filter(s => s[0] === 'sendMessage' && /^Привет всем/.test(s[2])).map(s => s[1])
  if (!bc.length || bc.includes(21) || bc.includes(1)) fail('рассылка: не дошла, или пришла тому, кто нажал /stop, или владельцу: ' + JSON.stringify(bc))
  if (!sent.some(s => s[0] === 'setMyCommands')) fail('меню команд «/» не выставлено')
  has(1, /Функции бота — нажмите/, '/settings владельцу не ответил')
  // Панель админа на кнопках
  has(1, /Вы владелец бота/, 'панель админа: /start владельца без кнопки «🛠 Админ»')
  if (!sent.some(s => s[0] === 'editMessageText' && String(s[1]) === '1' && /📊 Сегодня/.test(s[2]))) fail('панель админа: «Сегодня» не открылся')
  if (!sent.some(s => s[0] === 'editMessageText' && /Без ответа — выберите/.test(s[2]))) fail('панель админа: список вопросов не открылся')
  if (!to(9).includes('Ответ из панели') && !to(7).includes('Ответ из панели')) fail('панель админа: «Ответить» не отправил ответ человеку')
  if (!sent.some(s => s[0] === 'sendMessage' && /^Рассылка из бота/.test(s[2]) && String(s[1]) !== '1')) fail('панель админа: рассылка из бота не пошла людям')
  if (sent.some(s => s[0] === 'editMessageText' && String(s[1]) === '88')) fail('панель админа открылась у чужого человека')
  if (to(88).some(t => /Панель админа|Вы владелец/.test(t))) fail('чужой «🛠 Админ» что-то показал')
  if (!sent.some(s => s[0] === 'editMessageText' && /Пользователи бота: \d+/.test(s[2]))) fail('пользователи: список не открылся')
  if (!to(1).some(t => /📊 Сегодня[\s\S]*Новых людей за сутки/.test(t))) fail('/today не ответил владельцу')
  if (!sent.some(s => s[0] === 'sendPoll' && s[1] === 1)) fail('опрос: нет предпросмотра владельцу')
  if (!sent.some(s => s[0] === 'editMessageText' && /✅ Опрос запущен/.test(s[2]))) fail('опрос не запустился')
  if (!sent.some(s => s[0] === 'editMessageText' && /📊 (Итоги опросов|Голосов пока нет)/.test(s[2]))) fail('итоги опросов не показались')
  if (to(88).some(t => /Опрос всем/.test(t))) fail('чужой открыл «Опрос»')
  if (!to(1).some(t => /Скопируйте шаблон ниже/.test(t))) fail('шаблон с правкой не открылся')
  if (!sent.some(s => s[0] === 'sendMessage' && s[1] === '@apextestch' && s[2] === 'Новость для канала')) fail('пост в канал не опубликован')
  if (!sent.some(s => s[0] === 'sendMessage' && s[1] === '@apextestch' && /Разыгрываем подписку[\s\S]*🏆 Итоги:/.test(s[2]))) fail('конкурс из бота не опубликован')
  if (!sent.some(s => s[0] === 'editMessageText' && /✅ Конкурс b\w+ опубликован/.test(s[2]))) fail('конкурс: нет подтверждения владельцу')
  if (!sent.some(s => s[1] === '@apextestch' && /🏆 Итоги конкурса[\s\S]*@winx/.test(s[2]))) fail('конкурс из бота: итоги в канал не подведены сами')
  { const f = sent.find(s => s[0] === 'sendDocument'); if (!f) fail('копия данных: файл не отправлен'); else { if (!/"ulist"/.test(f[2]) || !/filename="apex-bot-/.test(f[2])) fail('копия данных: в файле нет данных'); if (/123456:AAAA/.test(f[2])) fail('копия данных: в файле токен') } }
  if (sent.filter(s => s[0] === 'sendDocument').length !== 1) fail('копию данных получил чужой')
  if (!sent.some(s => s[0] === 'editMessageText' && /🖥 Система[\s\S]*свой сервер/.test(s[2]))) fail('«Система и ошибки» не открылась')
  if (!sent.some(s => s[0] === 'editMessageText' && /Перезапустить бота\?/.test(s[2]))) fail('перезапуск: нет подтверждения')
  if (to(88).some(t => /Пост в|Копия данных/.test(t))) fail('чужой открыл пост в канал или копию')
  if (!to(23).some(t => /Вас назначили помощником/.test(t))) fail('помощник: не пришло назначение')
  if (!sent.some(s => s[0] === 'editMessageText' && s[1] === 23 && /📥 Без ответа/.test(s[2]))) fail('помощник: не видит вопросы')
  if (!to(23).some(t => /✅ Ответ отправлен/.test(t))) fail('помощник: ответ не отправился')
  if (!to(1).some(t => /🛟 Помощник @hlp ответил/.test(t))) fail('помощник: владельцу не пришло, кто ответил')
  if (to(23).some(t => /Рассылка всем/.test(t)) || sent.some(s => s[0] === 'editMessageText' && s[1] === 23 && /Функции бота/.test(s[2]))) fail('помощник открыл рассылку или функции')
  { const b = JSON.parse(JSON.parse(fs.readFileSync(path.join(tmp, 'data/kv.json'), 'utf8')).block || '[]'); if (b.includes(7)) fail('помощник смог забанить') }
  if (!sent.some(s => s[0] === 'editMessageText' && /🛟 Помощники \(1 из 5\)[\s\S]*помощник @hlp — 1/.test(s[2]))) fail('помощники: нет списка или счёта ответов')
  if (sent.some(s => s[0] === 'editMessageText' && s[1] === 22)) fail('не помощник открыл вопросы')
  if (!to(23).some(t => /Вы помощник поддержки/.test(t))) fail('помощник: кнопка «🛟 Помощник» не открыла меню')
  if (!to(61).some(t => /🛡 Официальный бот — только @test_bot/.test(t))) fail('нет предупреждения о поддельных ботах в приветствии')
  if (!to(62).some(t => /Напишите вопрос одним сообщением/.test(t))) fail('«Позвать человека» не ответил')
  if (!sent.some(s => s[0] === 'forwardMessage' && s[4] === 62)) fail('«Позвать человека»: сообщение не ушло человеку (ответили темой)')
  if (!to(62).some(t => /Вы в очереди: \d+-й/.test(t))) fail('нет места в очереди')
  if (!sent.some(s => s[0] === 'restrictChatMember' && s[1] === -100)) fail('капча: новичок не ограничен')
  if (!to(-100).some(t => /нажмите кнопку в течение 5 минут/.test(t))) fail('капча: нет сообщения с кнопкой')
  if (!sent.some(s => s[0] === 'restrictChatMember' && s[1] === -200) || sent.filter(s => s[0] === 'restrictChatMember').length !== 2) fail('капча: снять ограничение должен только сам новичок (ожидалось 2 вызова)')
  if (!to(-100).some(t => /Спамер, предупреждение 1 из 3/.test(t))) fail('/warn не сработал у админа')
  if (to(-100).some(t => /удалён из группы/.test(t)) || sent.some(s => s[0] === 'banChatMember')) fail('/ban сработал у не-админа')
  has(63, /Как установить/, 'умный поиск не понял опечатку')
  if (!sent.some(s => s[0] === 'editMessageReplyMarkup' && s[1] === 1)) fail('/settings: раздел не открылся')
  if (sent.some(s => s[0] === 'editMessageReplyMarkup' && s[1] === 67)) fail('/settings: чужой открыл раздел')
  if (!sent.some(s => s[0] === 'editMessageText' && s[1] === 1 && /👥 Работа с людьми/.test(s[2]))) fail('«🛠 Админ»: раздел не открылся')
  if (sent.some(s => s[0] === 'editMessageText' && s[1] === 68)) fail('«🛠 Админ»: чужой открыл раздел')
  if (!sent.some(s => s[0] === 'sendVideo' && s[1] === 69) || !sent.some(s => s[0] === 'sendVideo' && s[1] === 70)) fail('видеоинструкция не отправилась')
  if (!to(69).some(t => /Ваша подписка[\s\S]*активна[\s\S]*осталось 5 дн[\s\S]*5\.0 ГБ/.test(t))) fail('/sub не показал подписку')
  if (sent.some(s => /secret\.example|test-remna-token/.test(String(s[2])))) fail('бот показал ссылку подписки или токен панели')
  { const pt = sent.filter(s => s[0] === 'remnaPatch'); if (pt.length !== 1 || !/"uuid":"u-1"/.test(pt[0][2])) fail('бонус +7 дней: панель не продлила (или продлила чужим нажатием)') }
  if (!to(23).some(t => /добавили 7 дней/.test(t))) fail('бонус: человеку не пришло спасибо')
  if (to(71).some(t => /Начислено|Панель подписок/.test(t)) || sent.some(s => s[1] === 71 && s[0] === 'editMessageText')) fail('чужой нажал «+7 дней»')
  if (!sent.some(s => s[0] === 'setChatMenuButton')) fail('кнопка мини-приложения не поставлена')
  if (!to(64).some(t => /2\/3\. Какая версия/.test(t)) || !to(64).some(t => /3\/3\. Что не так/.test(t))) fail('/report: форма не задала вопросы')
  if (!to(1).some(t => /📝 Форма от[\s\S]*Samsung A52[\s\S]*не знаю[\s\S]*ничего не грузит/.test(t))) fail('/report: карточка не пришла владельцу')
  if (!to(65).some(t => /Викторина — вопрос 1/.test(t))) fail('/quiz не начался')
  if (!sent.some(s => s[0] === 'editMessageText' && s[1] === 65 && /вопрос 2 из/.test(s[2]))) fail('/quiz: нет следующего вопроса')
  if (!sent.some(s => s[0] === 'editMessageText' && s[1] === 65 && /Правильных ответов: 5 из 5/.test(s[2]))) fail('/quiz: нет итога')
  if (!sent.some(s => s[0] === 'forwardMessage' && s[4] === 66) || !to(1).some(t => /🧠 Перед этим человек читал тему/.test(t))) fail('память разговора: «не помогло» не передано людям')
  if (!to(1).some(t => /📥 (Без ответа|Вопросов без ответа нет)/.test(t))) fail('/queue не ответил владельцу')
  if (to(72).some(t => /📊 Сегодня/.test(t))) fail('/today сработал у чужого')
  if (!sent.some(s => s[0] === 'editMessageText' && /✅ Тема добавлена/.test(s[2]))) fail('«Сделать темой» не сработало')
  if (!sent.some(s => s[0] === 'editMessageText' && /🧩 Темы, созданные в боте/.test(s[2]))) fail('темы из бота не показались')
  if (!sent.some(s => s[0] === 'editMessageText' && /👤 .*\nНомер: 20/.test(s[2]))) fail('пользователи: карточка человека не открылась')
  { const b = JSON.parse(JSON.parse(fs.readFileSync(path.join(tmp, 'data/kv.json'), 'utf8')).block || '[]'); if (!b.includes(20)) fail('пользователи: «Забанить» не сработал'); if (b.includes(7)) fail('пользователи: чужой человек смог забанить') }
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
  { const w = await import(path.join(BOT, 'worker.js')), used = w.FEAT_GROUPS.flatMap(g => g[2]), miss = Object.keys(w.FEAT_NAMES).filter(k => !used.includes(k)); if (miss.length) fail('функции без раздела в /settings: ' + miss.join(', ')) }
  if (!rows.some(x => /\|status\|[^|]*\|0\|📋 Команды$/.test(x))) fail('меню → «Функции бота»: не видно, что /status выключен в боте или нет раздела')
}
const ws = fs.readFileSync(path.join(BOT, 'worker.js'), 'utf8')
if (!/x-telegram-bot-api-secret-token/.test(ws) || !/WEBHOOK_SECRET/.test(ws)) fail('worker.js: нет проверки секретного слова')
if ((ws.match(/^\/\/ V\|/m) || []).length === 0) fail('worker.js: нет «Что нового в боте»')
if (!ws.includes("async scheduled(")) fail('worker.js: нет расписания для Cloudflare (scheduled) — рассылка встанет, если боту не пишут')

fs.rmSync(tmp, { recursive: true, force: true })
console.log(errs.length ? `✗ Бот и меню: ошибок ${errs.length}` : '✓ Бот и меню — без ошибок')
process.exit(errs.length ? 1 : 0)
