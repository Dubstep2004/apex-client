// Рисует картинки-инструкции из scene.html: живые WebP для README (docs/guide) и неподвижные для справки в приложении (assets/help).
// Запуск из корня репозитория: NODE_PATH=$(npm root -g) node tools/guide-update/render.js
const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs'), path = require('path'), os = require('os');
const N = 36; // кадров анимации, плюс пауза на последнем
(async () => {
  const b = await chromium.launch();
  const page = await b.newPage({ viewport: { width: 1280, height: 760 } });
  for (const [s, name] of [['install', 'gh_install'], ['update', 'gh_update']]) {
    await page.goto('file://' + path.resolve(__dirname, 'scene.html') + '?s=' + s);
    await page.evaluate(() => document.fonts.ready);
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'guide-'));
    for (let i = 0; i < N; i++) {
      await page.evaluate((t) => window.frame(t), i / (N - 1));
      await page.screenshot({ path: path.join(dir, String(i).padStart(3, '0') + '.png') });
    }
    execFileSync('python3', [path.resolve(__dirname, 'pack.py'), dir, 'docs/guide/' + name + '.webp', 'app/src/main/assets/help/' + name + '.webp'], { stdio: 'inherit' });
  }
  await b.close();
})();
