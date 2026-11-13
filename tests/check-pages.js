// Автопроверка пульта и страницы-приглашения: открывает их в настоящем браузере (Chromium),
// подменяет GitHub поддельными данными и ищет ошибки JavaScript, битые ссылки и вылезание за край экрана.
// Запуск: node tests/check-pages.js  (нужен playwright с Chromium)
const { chromium } = require('playwright')
const fs = require('fs'), http = require('http'), path = require('path')
const DOCS = path.join(__dirname, '..', 'docs')
const TYPES = { '.html': 'text/html; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.webp': 'image/webp', '.json': 'application/json', '.webmanifest': 'application/manifest+json' }
const file = u => { let p = path.join(DOCS, decodeURIComponent(u.split('?')[0].split('#')[0])); if (fs.existsSync(p) && fs.statSync(p).isDirectory()) p = path.join(p, 'index.html'); return fs.existsSync(p) ? p : null }
const srv = http.createServer((q, r) => { const p = file(q.url); if (!p) { r.writeHead(404); return r.end() } r.setHeader('content-type', TYPES[path.extname(p)] || 'application/octet-stream'); r.end(fs.readFileSync(p)) })
const app = fs.readFileSync(path.join(DOCS, 'config/app.json'), 'utf8'), b64 = s => Buffer.from(s).toString('base64')
const releases = [{ id: 1, tag_name: 'v9.9.9', name: 'APEX CLIENT 9.9.9', prerelease: false, draft: false, published_at: new Date().toISOString(), body: '- проверка', assets: [{ id: 5, name: 'apex-client-9.9.9.apk', size: 6e7, download_count: 3, browser_download_url: 'https://github.com/x.apk' }] }]
const problems = []
const bad = (where, what) => { problems.push(where + ': ' + what); console.log('::error title=' + where + '::' + what) }

async function github(page) {
  await page.route('https://api.github.com/**', async rt => {
    const u = rt.request().url(), m = rt.request().method()
    const J = (o, s = 200) => rt.fulfill({ status: s, contentType: 'application/json', body: JSON.stringify(o) })
    if (m === 'PUT') return J({ content: { sha: 'new' } })
    if (m === 'PATCH') return J(releases[0])
    if (/\/repos\/[^/]+\/[^/]+$/.test(u)) return J({ permissions: { push: true } })
    if (u.includes('/contents/docs/config/app.json')) return J({ sha: 's1', content: b64(app) })
    if (u.includes('/contents/')) { const f = path.join(__dirname, '..', u.split('/contents/')[1].split('?')[0]); return fs.existsSync(f) ? J({ sha: 's', content: b64(fs.readFileSync(f, 'utf8')) }) : J({ message: 'Not Found' }, 404) }
    if (u.includes('/releases')) return J(u.includes('page=2') ? [] : u.includes('/latest') ? releases[0] : releases)
    if (u.includes('/actions/runs')) return J({ workflow_runs: (globalThis.CI_FAIL ? ['Build APEX CLIENT', 'Проверка пульта и страниц'] : ['Build APEX CLIENT']).map((name, i) => ({ name, status: 'completed', conclusion: i ? 'failure' : 'success', updated_at: new Date().toISOString(), html_url: 'https://github.com/run/' + i })) })
    if (u.includes('/commits/')) return J({ files: [] })
    if (u.includes('/commits')) return J([{ sha: 'a', commit: { message: 'Пульт: проверка', author: { name: 'ci', date: new Date().toISOString() } } }])
    return J({})
  })
  await page.route(/raw\.githubusercontent|cdn\.jsdelivr/, rt => rt.fulfill({ status: 200, contentType: 'application/json', headers: { 'access-control-allow-origin': '*' }, body: app }))
  await page.route(/fonts\.googleapis|fonts\.gstatic|t\.me/, rt => rt.abort())
}
function watch(page, where) {
  page.on('pageerror', e => bad(where, 'ошибка JavaScript: ' + e.message))
  page.on('response', r => { if (r.status() >= 400 && r.url().startsWith('http://localhost')) bad(where, 'нет файла ' + new URL(r.url()).pathname) })
}
async function edges(page, where) {
  const ov = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)
  if (ov > 1) bad(where, 'страница шире экрана на ' + ov + ' px')
  const links = await page.evaluate(() => [...document.querySelectorAll('a[href]')].map(a => a.getAttribute('href')).filter(h => /^(\.\.?\/|[a-z-]+\.(html|svg|png|json)$)/.test(h)))
  for (const h of links) if (!file(new URL(h, page.url()).pathname)) bad(where, 'битая ссылка ' + h)
}

async function dragRow(p, rowSel, from, to) { // перетащить строку from на место to (за ⠿); у края экрана ждёт автопрокрутку
  await p.evaluate(([s, f]) => document.querySelectorAll(s)[f].querySelector('.grip').scrollIntoView({ block: 'center' }), [rowSel, from]); await p.waitForTimeout(100)
  const H = p.viewportSize().height, g = await (await p.$$(rowSel + ' .grip'))[from].boundingBox(), x = g.x + g.width / 2
  let y = g.y + g.height / 2; await p.mouse.move(x, y); await p.mouse.down()
  for (let k = 0; k < 120; k++) {
    const tb = await (await p.$$(rowSel))[to].boundingBox(), want = to > from ? tb.y + tb.height * .8 : tb.y + tb.height * .2
    const goal = Math.min(H - 5, Math.max(5, want)); y += Math.sign(goal - y) * Math.min(40, Math.abs(goal - y))
    await p.mouse.move(x, y); await p.waitForTimeout(30)
    if (Math.abs(want - y) < 3) break
  }
  await p.mouse.up(); await p.waitForTimeout(250)
}
;(async () => {
  await new Promise(r => srv.listen(8199, r))
  const b = await chromium.launch()
  for (const width of [390, 1366]) {
    // Пульт: вход, все вкладки, сохранение
    const p = await b.newPage({ viewport: { width, height: 900 } }); const W = 'Пульт ' + width
    watch(p, W); await github(p); p.on('dialog', d => d.accept())
    await p.goto('http://localhost:8199/admin/')
    await p.fill('#token', 'github_pat_test'); await p.fill('#newPin', '1234'); await p.click('#loginBtn'); await p.waitForTimeout(1500)
    if (await p.isHidden('#app')) bad(W, 'не открылся после входа')
    const tabs = await p.evaluate(() => TABS.map(t => t[0]))
    for (const t of tabs) { await p.evaluate(id => showTab(id), t); await p.waitForTimeout(150); await edges(p, W + ' → ' + t) }
    await p.evaluate(() => showTab('banner')); await p.click('#addBanner'); await p.fill('#banners .item:last-child textarea', 'Проверка')
    await p.click('#saveApp'); await p.waitForTimeout(400)
    if (!(await p.textContent('#toast')).includes('Сохранено')) bad(W, 'не сохраняет баннер')
    // 2.13: календарь, проверка перед сохранением, поиск по содержимому, быстрые действия, шаблон объявления
    await p.evaluate(() => showTab('home'))
    if (!(await p.$$('#calList .row')).length) bad(W, 'календарь пустой, хотя баннер есть')
    await edges(p, W + ' → обзор с календарём')
    await p.evaluate(() => { banners.unshift(newBanner({ text: 'Старый баннер', level: 'info', to: Date.now() - 864e5 })); renderBanners(); changed() })
    await p.click('#saveApp'); await p.waitForTimeout(500)
    if (!(await p.isVisible('#warnDlg'))) bad(W, 'нет предупреждения о закончившемся баннере'); else await p.click('#warnNo')
    await p.fill('#q', 'Старый баннер'); await p.waitForTimeout(100)
    if (!(await p.textContent('#qres')).includes('Баннер')) bad(W, 'поиск не находит текст баннера')
    await p.fill('#q', '')
    await p.evaluate(() => showTab('home')); await p.click('[data-qa=expired]'); if (await p.evaluate(() => banners.some(b => b.text === 'Старый баннер'))) bad(W, '«Удалить закончившееся» не удалило баннер')
    await p.evaluate(() => showTab('announce')); await p.click('#aTpls [data-atpl="0"]')
    if (!(await p.inputValue('#aTitle'))) bad(W, 'шаблон объявления не подставился')
    await p.evaluate(() => { $('aClear').click(); showTab('home') })
    if (!(await p.$$('#relSum div')).length) bad(W, 'нет итогов скачиваний')
    // 2.15: неделя, ответы, журнал и сравнение, ссылки, горячие клавиши, большой экран, свои цвета
    if (!(await p.$$('#weekSum div')).length) bad(W, 'нет блока «Неделя»')
    await p.evaluate(() => showTab('answers')); await p.waitForTimeout(100)
    if ((await p.$$('#ansList .ansi')).length < 5) bad(W, 'вкладка «Ответы» пустая')
    await p.fill('#ansT', 'Тест'); await p.fill('#ansX', 'Ответ'); await p.click('#ansAdd'); if (!(await p.textContent('#ansOwn')).includes('Тест')) bad(W, 'свой ответ не добавился')
    await edges(p, W + ' → ответы')
    await p.evaluate(() => showTab('tg')); await p.waitForTimeout(100); await edges(p, W + ' → Telegram')
    // 2.19: канал и группа через запятую — «Проверить» находит оба (Telegram подменён)
    await p.route('https://api.telegram.org/**', rt => { const chat = new URLSearchParams(rt.request().postData() || '').get('chat_id'); rt.fulfill({ status: 200, contentType: 'application/json', headers: { 'access-control-allow-origin': '*' }, body: JSON.stringify({ ok: true, result: chat === '@apex_chat' ? { type: 'supergroup', title: 'Группа APEX' } : { type: 'channel', title: 'Канал APEX' } }) }) })
    await p.fill('#tgTok', '123456789:AAbbccddeeffgghhiijjkkllmmnnooppqq'); await p.fill('#tgChat', '@apex_news, @apex_chat'); await p.click('#tgSave'); await p.waitForTimeout(300)
    await p.click('#tgTest'); await p.waitForTimeout(500)
    const tt = await p.textContent('#toast'); if (!tt.includes('канал Канал APEX') || !tt.includes('группа Группа APEX')) bad(W, 'Telegram: канал и группа не нашлись: ' + tt)
    await p.click('#tgForget')
    // 2.20: без VPN — Telegram недоступен, токена нет: «Проверить» и сообщение идут через робота GitHub (outbox → итог в tg-state)
    await p.unroute('https://api.telegram.org/**'); await p.route('https://api.telegram.org/**', rt => rt.abort())
    const ids = []
    await p.route('https://api.github.com/**/contents/docs/tg/outbox.json*', rt => { if (rt.request().method() === 'PUT') { const box = JSON.parse(Buffer.from(JSON.parse(rt.request().postData()).content, 'base64').toString()); ids.push(...box.items.map(i => i.id)); return rt.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ content: { sha: 'ob' } }) }) } rt.fulfill({ status: 404, contentType: 'application/json', body: '{"message":"Not Found"}' }) })
    await p.route('https://api.github.com/**/contents/docs/stats/tg-state.json*', rt => rt.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ sha: 'st', content: Buffer.from(JSON.stringify({ results: Object.fromEntries(ids.map(i => [i, { ok: true, done: ['канал Канал APEX'] }])) })).toString('base64') }) }))
    await p.click('#tgTest'); await p.waitForFunction(() => /через GitHub/.test(document.querySelector('#toast').textContent), null, { timeout: 15000 }).catch(() => bad(W, 'Telegram без VPN: проверка через GitHub не сработала: ' + ''))
    await p.fill('#tgText', 'Тест через GitHub'); await p.click('#tgSend')
    await p.waitForFunction(() => /Отправлено в канал/.test(document.querySelector('#toast').textContent), null, { timeout: 15000 }).catch(() => bad(W, 'Telegram без VPN: сообщение через GitHub не ушло'))
    if (ids.length < 2) bad(W, 'Telegram без VPN: пульт не положил задание для робота')
    // 2.21: «Новая версия» — без звёздочек и с картинкой через робота
    await p.evaluate(() => renderTgPick()); const vb = await p.$('#tgPick [data-tp]:has-text("Новая версия")')
    if (!vb || !(await vb.textContent()).includes('Новая версия')) bad(W, 'нет шаблона «Новая версия»')
    else {
      await vb.click(); const vt = await p.inputValue('#tgText')
      if (vt.includes('**') || !vt.includes('• проверка')) bad(W, '«Новая версия»: текст с разметкой: ' + vt.slice(0, 80))
      if (!(await p.isVisible('#tgPhotoRow'))) bad(W, '«Новая версия»: нет переключателя картинки')
      const n0 = ids.length; await p.click('#tgSend')
      await p.waitForFunction(() => /с картинкой/.test(document.querySelector('#toast').textContent), null, { timeout: 15000 }).catch(() => bad(W, '«Новая версия» с картинкой не ушла'))
      if (ids.length <= n0) bad(W, '«Новая версия»: нет задания для робота')
    }
    // 2.22: опрос с картинкой — через робота
    await p.fill('#tgQ', 'Какой сервер добавить?'); await p.fill('#tgOpts', 'Япония\nСША'); const n1 = ids.length; await p.click('#tgPoll')
    await p.waitForFunction(() => /Картинка и опрос/.test(document.querySelector('#toast').textContent), null, { timeout: 15000 }).catch(() => bad(W, 'опрос с картинкой не ушёл'))
    if (ids.length <= n1) bad(W, 'опрос с картинкой: нет задания для робота')
    // 2.23: счётчик и предпросмотр, кнопки, отложенный пост, свой шаблон, журнал
    await p.evaluate(() => { $('tgText')._ver = ''; $('tgPhotoRow').hidden = true })
    await p.fill('#tgText', 'Техработы сегодня\nС 22:00 до 23:00'); await p.check('#tgCard'); await p.check('#tgBDl'); await p.check('#tgSilent')
    if (!(await p.textContent('#tgCount')).includes('/ 1024')) bad(W, 'счётчик знаков не учитывает картинку')
    if (!(await p.$('#tgPrev .tgcard')) || !(await p.textContent('#tgPrev')).includes('📥 Скачать')) bad(W, 'предпросмотр поста без карточки или кнопки')
    await p.fill('#tgText', 'x'.repeat(1030)); if (!(await p.$eval('#tgCount', e => e.classList.contains('bad')))) bad(W, 'счётчик не краснеет при превышении')
    await p.fill('#tgText', 'Отложенный пост')
    const later = await p.evaluate(() => { const d = new Date(Date.now() + 3 * 3600e3); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16) }); await p.fill('#tgWhen', later)
    const n2 = ids.length; await p.click('#tgSend'); await p.waitForTimeout(800)
    if (ids.length <= n2) bad(W, 'отложенный пост: нет задания для робота')
    if (!(await p.textContent('#toast')).includes('Запланировано')) bad(W, 'отложенный пост: нет подтверждения')
    await p.uncheck('#tgCard'); await p.uncheck('#tgBDl'); await p.uncheck('#tgSilent')
    await p.evaluate(() => { tgState = { version: 'v0', log: [{ at: Date.now(), kind: 'Сообщение', text: 'Пробный пост', ok: true }, { at: Date.now(), kind: 'Опрос', text: 'Q', ok: false, err: 'чат не найден' }], last: [{ at: Date.now(), kind: 'Сообщение', text: 'Пробный пост', ids: { '@a': [1] } }], members: { 1: [['2026-11-01', 100], ['2026-11-13', 120]] } }; renderTgState() })
    if (!(await p.textContent('#tgLog')).includes('чат не найден')) bad(W, 'журнал канала не показывает ошибки')
    if (await p.isDisabled('#tgDel')) bad(W, '«Удалить последний пост» недоступна')
    if (!(await p.isVisible('#tgVerBanner'))) bad(W, 'нет плашки «версия не объявлена»')
    if (!(await p.textContent('#tgStat')).includes('120')) bad(W, 'подписчики не посчитаны')
    await edges(p, W + ' → Telegram 2.23')
    if (process.env.SHOT) { await p.evaluate(() => { $('tgText').value = 'Техработы сегодня\nС 22:00 до 23:00 сервер Германия недоступен'; $('tgCard').checked = $('tgBDl').checked = $('tgBHow').checked = true; tgPreview(); document.querySelector('section[data-tab=tg] .block:nth-of-type(4)')?.scrollIntoView() }); await p.screenshot({ path: process.env.SHOT + '-' + width + '.png', fullPage: true }) }
    await p.unroute('https://api.telegram.org/**')
    await p.evaluate(() => showTab('history')); await p.waitForTimeout(600)
    if (!(await p.$$('#journal > div')).length) bad(W, 'журнал изменений пустой')
    await p.click('#cmpGo'); await p.waitForTimeout(400); if (!(await p.innerHTML('#cmpOut'))) bad(W, 'сравнение сохранений ничего не показало')
    await edges(p, W + ' → история')
    await p.evaluate(() => showTab('checks')); await p.evaluate(() => { banners.unshift(newBanner({ text: 'Ссылка', url: 'http://плохая' })); renderBanners(); changed() })
    await p.click('#linkCheck'); await p.waitForTimeout(1500); if (!(await p.textContent('#linkList')).includes('неправильная')) bad(W, 'проверка ссылок не нашла плохую ссылку')
    await p.evaluate(() => { banners.shift(); renderBanners(); changed(); showTab('home') })
    await p.keyboard.press('?'); if (!(await p.isVisible('#keysDlg'))) bad(W, '«?» не показывает горячие клавиши'); else await p.click('#keysOk')
    await p.keyboard.press('2'); if (await p.evaluate(() => curTab === 'home')) bad(W, 'клавиша 2 не переключает вкладку')
    await p.evaluate(() => { showTab('home'); openBig() }); if (!(await p.$$('#bigIn .card')).length) bad(W, 'большой экран пустой'); await p.evaluate(() => closeBig())
    await p.evaluate(() => { $('myC1').value = '#ff0000'; $('myC1').dispatchEvent(new Event('input')) })
    if ((await p.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--go1').trim())) !== '#ff0000') bad(W, 'свои цвета не применились')
    await p.evaluate(() => $('myCOff').click())
    // 2.16: сортировка перетаскиванием (блоки визитки) и праздничное оформление
    await p.evaluate(() => showTab('site')); await p.waitForTimeout(200)
    await p.evaluate(() => $('sects').children[1].scrollIntoView({ block: 'center' })); await p.waitForTimeout(100)
    const first = await p.evaluate(() => sects[0].id), g = await p.$('#sects .sect:first-child .grip'), gb = await g.boundingBox(), third = await (await p.$('#sects .sect:nth-child(3)')).boundingBox()
    await p.mouse.move(gb.x + gb.width / 2, gb.y + gb.height / 2); await p.mouse.down()
    for (let k = 1; k <= 12; k++) { await p.mouse.move(gb.x + gb.width / 2, gb.y + (third.y + third.height * .75 - gb.y) * k / 12); await p.waitForTimeout(30) }
    await p.mouse.up(); await p.waitForTimeout(250)
    if (await p.evaluate(f => sects[0].id === f || sects.findIndex(x => x.id === f) !== 2, first)) bad(W, 'перетаскивание блоков визитки не поменяло порядок: ' + await p.evaluate(() => sects.map(x => x.id).join(',')))
    await p.evaluate(() => { $('sectsReset').click(); $('sHoliday').value = 'march8' }); if ((await p.evaluate(() => collectSite().show.holiday)) !== 'march8') bad(W, 'праздничное оформление не попадает в настройки визитки')
    await p.evaluate(() => { fillSite(files[SITE]?.data) })
    // 2.18: перетаскивание баннеров и вкладок меню
    await p.evaluate(() => { banners.unshift(newBanner({ text: 'Б-первый' }), newBanner({ text: 'Б-второй' })); renderBanners(); changed(); showTab('banner') }); await p.waitForTimeout(200)
    await dragRow(p, '#banners > .item', 0, 1)
    if (!(await p.evaluate(() => banners[0].text === 'Б-второй' && banners[1].text === 'Б-первый'))) bad(W, 'перетаскивание баннеров не работает: ' + await p.evaluate(() => banners.slice(0, 2).map(b => b.text).join(',')))
    await p.evaluate(() => { banners = banners.filter(b => !b.text.startsWith('Б-')); renderBanners(); changed() })
    if (width > 760) {
      await p.click('#navEdit'); await p.waitForTimeout(200)
      const sel = '#nav .ngrp[data-g="users"] > button', before = await p.$$eval(sel, b => b.map(x => x.dataset.id))
      await dragRow(p, sel, 0, 2)
      const after = await p.$$eval(sel, b => b.map(x => x.dataset.id))
      if (after[2] !== before[0]) bad(W, 'перетаскивание вкладок меню не работает: ' + after.join(','))
      await p.evaluate(() => { localStorage.removeItem('apex_admin_nav'); $('nav').classList.remove('editing'); renderNav() })
    }
    // 2.17: праздник одной кнопкой, виды визитки
    await p.evaluate(() => showTab('banner')); await p.click('#holBtn'); await p.click('#holKinds [data-hk="march8"]'); await p.click('#holOk'); await p.waitForTimeout(500)
    if (!(await p.evaluate(() => banners.some(b => b.name.includes('8 Марта') && b.to > Date.now()) && $('sHoliday').value === 'auto'))) bad(W, '«Праздник одной кнопкой» не добавил баннер или оформление')
    await p.evaluate(() => { banners = banners.filter(b => !b.name.includes('8 Марта')); renderBanners(); changed(); showTab('site') }); await p.waitForTimeout(300)
    await p.fill('#presetName', 'Проверка'); await p.click('#presetAdd'); await p.waitForTimeout(400)
    if (!(await p.textContent('#presetList')).includes('Проверка')) bad(W, 'вид визитки не сохранился')
    await edges(p, W + ' → визитка с видами')
    await p.evaluate(() => showTab('banner')); await p.click('#oldVerBtn'); await p.waitForTimeout(300)
    if (!(await p.evaluate(() => banners[0]?.maxCode > 0))) bad(W, 'баннер для старых версий без ограничения версии')
    await p.evaluate(() => { banners.shift(); renderBanners(); changed(); showTab('home') })
    // Обзор: карточка «Состояние»
    if (!(await p.$$('#ciList li')).length) bad(W, 'пустая карточка «Состояние»')
    if (await p.isVisible('#ciwarn')) bad(W, 'красная полоса при зелёных сборках')
    // Справка: поиск, развернуть, переход на вкладку
    await p.evaluate(() => showTab('help')); await p.fill('#hq', 'техработы'); await p.waitForTimeout(100)
    if (!(await p.$$('section[data-tab=help] mark.hit')).length) bad(W, 'поиск по справке ничего не находит')
    await p.fill('#hq', 'ъъъъ'); if (await p.isHidden('#hNo')) bad(W, 'поиск по справке: нет сообщения «ничего не нашлось»')
    await p.fill('#hq', ''); await p.click('#hOpen')
    if (await p.evaluate(() => [...document.querySelectorAll('section[data-tab=help] details')].some(d => !d.open))) bad(W, '«Развернуть всё» не раскрыло разделы')
    await edges(p, W + ' → справка раскрыта')
    await p.click('[data-open=servers], [data-open=update]'); if (await p.evaluate(() => curTab === 'help')) bad(W, 'кнопка «Открыть вкладку» не переключает')
    await p.close()
    // Упавшая проверка → красная полоса
    globalThis.CI_FAIL = true
    const f = await b.newPage({ viewport: { width, height: 900 } }); watch(f, W + ' (сборка упала)'); await github(f)
    await f.goto('http://localhost:8199/admin/'); await f.fill('#token', 'github_pat_test'); await f.fill('#newPin', '1234'); await f.click('#loginBtn'); await f.waitForTimeout(1500)
    if (await f.isHidden('#ciwarn')) bad(W, 'нет красной полосы, когда проверка упала')
    globalThis.CI_FAIL = false; await f.close()
    // Приглашение: без подписки и с подпиской
    for (const u of ['/invite/', '/invite/#https%3A%2F%2Fsub.knownhosts.xyz%2Ftest']) {
      const q = await b.newPage({ viewport: { width, height: 900 } }); const Q = 'Приглашение ' + width + (u.includes('#') ? ' (с подпиской)' : '')
      watch(q, Q); await github(q); await q.route(/cdnjs/, rt => rt.abort())
      await q.goto('http://localhost:8199' + u); await q.waitForTimeout(600); await edges(q, Q); await q.close()
    }
  }
  await b.close(); srv.close()
  console.log(problems.length ? `\nНайдено проблем: ${problems.length}` : '\n✓ Пульт и приглашение — без ошибок')
  process.exit(problems.length ? 1 : 0)
})()
