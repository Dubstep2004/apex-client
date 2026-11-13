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


// Видео «Как пользоваться пультом» для Справки: настоящий пульт с ненастоящими данными, подписи и курсор.
// Запуск: cd tests && NODE_PATH=$(npm root -g) node pult-video.js <out.mp4> [проверочные секунды]
const { spawn } = require('child_process')
const OUT = process.argv[2], CHECK = process.argv[3], FPS = 30
const SCENES = [
  // [секунды, вкладка, подпись, шаги в странице (t от 0 до 1)]
  [4, 'home', 'Обзор — всё главное на одном экране', `scrollTo(0, ease(t) * 520); cursor(1040, 300 + t * 120)`],
  [5, 'banner', 'Баннер для всех: текст, расписание, устройства', `if (!window._b) { _b = 1; $('addBanner').click(); scrollTo(0, 0) }
    const ta = document.querySelector('#banners .item:first-child textarea'); const txt = 'Сегодня с 22:00 до 23:00 техработы — автопилот всё сделает сам'
    ta.value = txt.slice(0, Math.round(txt.length * Math.min(1, t * 1.4))); const r = ta.getBoundingClientRect(); cursor(r.left + 60 + t * 200, r.top + 30)`],
  [4.5, 'servers', 'Серверы: техработы, «медленно», «советуем»', `if (!window._m) { _m = 1; marks.push({ name: 'Германия', state: 'maintenance', note: 'обновляем оборудование', until: Date.now() + 2 * 36e5, preview: false }); renderMarks(); changed() }
    const r = document.querySelector('#marks .mark:last-child')?.getBoundingClientRect(); if (r) cursor(r.left + 80, r.top + 20 + t * 8)`],
  [3.5, 'home', '«Сохранить» — приложения получат за 1–15 минут', `const b = $('saveApp').getBoundingClientRect(); cursor(b.left + b.width / 2 - (1 - ease(Math.min(1, t * 2))) * 300, b.top + b.height / 2)
    if (t > .55 && !window._s) { _s = 1; $('toast').textContent = 'Сохранено — приложения получат изменения за несколько минут'; $('toast').classList.add('show') }`],
  [4.5, 'site', 'Визитка: блоки, праздники, вид сайта', `if (!window._v) { _v = 1; $('sHoliday').value = 'auto'; $('sects').scrollIntoView({ block: 'center' }) }
    const r = $('sects').getBoundingClientRect(); cursor(r.left + 30, r.top + 40 + ease(t) * 150)`],
  [4.5, 'help', 'Справка и поиск — ответ на любой вопрос', `const q = $('hq'), s = 'техработы'; q.value = s.slice(0, Math.round(s.length * Math.min(1, t * 1.6))); q.dispatchEvent(new Event('input'))
    const r = q.getBoundingClientRect(); cursor(r.left + 120, r.top + r.height / 2)`],
]
;(async () => {
  await new Promise(r => srv.listen(8199, r)); const b = await chromium.launch()
  const p = await b.newPage({ viewport: { width: 1280, height: 720 } }); await github(p)
  await p.addInitScript(() => { localStorage.setItem('apex_admin_design', 'glass') })
  await p.goto('http://localhost:8199/admin/'); await p.fill('#token', 'github_pat_test'); await p.fill('#newPin', '1234'); await p.click('#loginBtn'); await p.waitForTimeout(1800)
  await p.evaluate(() => {
    document.documentElement.classList.add('nomotion')
    const st = document.createElement('style'); st.textContent = '#vcap{position:fixed;left:50%;bottom:34px;transform:translateX(-50%);z-index:999;background:#0b0a1aee;color:#fff;font:700 26px Manrope,sans-serif;padding:16px 28px;border-radius:20px;border:1px solid #ffffff22;box-shadow:0 20px 60px #0008;white-space:nowrap}#vcur{position:fixed;z-index:1000;width:30px;height:30px;margin:-4px 0 0 -4px;pointer-events:none}.savebar{z-index:5}'
    document.head.append(st); const c = document.createElement('div'); c.id = 'vcap'; document.body.append(c)
    const k = document.createElement('div'); k.id = 'vcur'; k.innerHTML = '<svg viewBox="0 0 24 24" width="30" height="30"><path d="M4 2l16 9-7 2-3 7z" fill="#fff" stroke="#0b0a1a" stroke-width="1.5"/></svg>'; document.body.append(k)
    window.cursor = (x, y) => { k.style.left = x + 'px'; k.style.top = y + 'px' }; window.ease = x => x < .5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2
  })
  let ff, tmp
  if (!CHECK) { tmp = OUT.replace(/\.mp4$/, '.part.mp4'); ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '27', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', tmp], { stdio: ['pipe', 'inherit', 'inherit'] }) }
  let T = 0; const checks = CHECK ? CHECK.split(',').map(Number) : []
  for (const [dur, tab, cap, step] of SCENES) {
    await p.evaluate(([tab, cap]) => {
      showTab(tab); $('vcap').textContent = cap; $('toast').classList.remove('show')
      const save = cap.includes('Сохранить'), bar = document.querySelector('.savebar')
      if (bar) bar.style.visibility = save ? '' : 'hidden'
      Object.assign($('vcap').style, save ? { top: '22px', bottom: 'auto' } : { top: 'auto', bottom: '34px' })
    }, [tab, cap]); await p.waitForTimeout(250)
    const fn = new Function('t', step), n = Math.round(dur * FPS)
    await p.evaluate(s => { window._step = new Function('t', s) }, step)
    for (let i = 0; i < n; i++, T += 1 / FPS) {
      await p.evaluate(t => _step(t), i / n)
      if (CHECK) { const c = checks.find(x => Math.abs(x - T) < .5 / FPS); if (c !== undefined) await p.screenshot({ path: `/tmp/claude-0/-home-claude/3e49600a-29f4-5009-9e20-d1ca8a689380/scratchpad/pv-${c}.jpg`, type: 'jpeg', quality: 80 }); continue }
      const buf = await p.screenshot({ type: 'jpeg', quality: 90 }); if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r))
    }
  }
  if (ff) { ff.stdin.end(); const code = await new Promise(r => ff.on('close', r)); if (code === 0) fs.renameSync(tmp, OUT) }
  await b.close(); srv.close(); console.log('секунд:', T.toFixed(1))
})()
