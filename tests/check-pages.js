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
    await p.click('[data-qa=expired]'); if (await p.evaluate(() => banners.some(b => b.text === 'Старый баннер'))) bad(W, '«Удалить закончившееся» не удалило баннер')
    await p.evaluate(() => showTab('announce')); await p.click('#aTpls [data-atpl="0"]')
    if (!(await p.inputValue('#aTitle'))) bad(W, 'шаблон объявления не подставился')
    await p.evaluate(() => { $('aClear').click(); showTab('home') })
    if (!(await p.$$('#relSum div')).length) bad(W, 'нет итогов скачиваний')
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
