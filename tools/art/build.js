// Renders every sprite (SVG written in src/*.js) to PNG with headless Chromium.
// Usage: node tools/art/build.js [filter]   -> tools/art/out/**.png, then ./gradlew :tools:packAtlas
'use strict';
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const OUT = path.join(__dirname, 'out');
const groups = ['terrain', 'objects', 'characters', 'crops', 'icons', 'ui'];

async function main() {
  const filter = process.argv[2] || '';
  const sprites = [];
  for (const gname of groups) {
    const file = path.join(__dirname, 'src', gname + '.js');
    if (!fs.existsSync(file)) continue;
    for (const s of require(file)()) if (s.name.includes(filter)) sprites.push(s);
  }
  const extra = path.join(__dirname, 'src', 'appicon.js');
  const appIcons = fs.existsSync(extra) && !filter ? require(extra)() : [];

  const browser = await chromium.launch();
  const page = await browser.newPage({ deviceScaleFactor: 1 });
  let n = 0;
  for (const s of [...sprites, ...appIcons]) {
    const m = s.svg.match(/width="(\d+(?:\.\d+)?)" height="(\d+(?:\.\d+)?)"/);
    const w = Math.ceil(+m[1]), h = Math.ceil(+m[2]);
    await page.setViewportSize({ width: w, height: h });
    await page.setContent(`<html><body style="margin:0;background:transparent">${s.svg}</body></html>`);
    const target = s.out ? s.out : path.join(OUT, s.name + '.png');
    fs.mkdirSync(path.dirname(target), { recursive: true });
    await page.screenshot({ path: target, omitBackground: true, clip: { x: 0, y: 0, width: w, height: h } });
    n++;
  }
  await browser.close();
  console.log(`rendered ${n} sprites`);
}

main().catch((e) => { console.error(e); process.exit(1); });
