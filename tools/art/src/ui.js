// UI skin pieces (nine-patches are cut in code with the split sizes noted here) and the logo.
'use strict';
const fs = require('fs');
const path = require('path');
const S = require('./svg');
const { shade, rect, circle, ellipse, rrect } = S;

// Nine-patch sources: 96x96 with 32px corners unless noted.
function panel() {
  let d = S.path(rrect(4, 6, 88, 88, 26), '#5a4430', { opacity: 0.25 });
  d += S.path(rrect(4, 2, 88, 88, 26), '#fff6e3', { stroke: '#d8b98a', strokeWidth: 4 });
  d += S.path(rrect(10, 8, 76, 30, 18), '#ffffff', { opacity: 0.35 });
  return S.svg(96, 96, d);
}
function panelInset() {
  return S.svg(96, 96, S.path(rrect(3, 3, 90, 90, 22), '#f6e7c9', { stroke: '#e3cba3', strokeWidth: 3 }));
}
function dark() {
  return S.svg(96, 96, S.path(rrect(0, 0, 96, 96, 30), '#2b2118', { opacity: 0.82 }));
}
function header() {
  let d = S.path(rrect(2, 2, 92, 92, 26), '#6aa84f', { stroke: '#4f8a3a', strokeWidth: 4 });
  d += S.path(rrect(10, 8, 76, 26, 14), '#ffffff', { opacity: 0.18 });
  return S.svg(96, 96, d);
}
// Buttons: 96x80, corners 28, a darker lip at the bottom when up.
function button(c, down) {
  const lip = shade(c, -0.28);
  let d = '';
  if (!down) d += S.path(rrect(2, 8, 92, 70, 26), lip);
  const y = down ? 8 : 2;
  d += S.path(rrect(2, y, 92, 68, 26), c, { stroke: shade(c, -0.32), strokeWidth: 3 });
  d += S.path(rrect(12, y + 6, 72, 22, 12), '#ffffff', { opacity: down ? 0.12 : 0.28 });
  return S.svg(96, 80, d);
}
function slot(sel) {
  let d = S.path(rrect(4, 4, 88, 88, 20), sel ? '#fff3c4' : '#fbeed5', { stroke: sel ? '#f2a93b' : '#d6b789', strokeWidth: sel ? 7 : 4 });
  return S.svg(96, 96, d);
}
const bar = (c) => S.svg(48, 28, S.path(rrect(1, 1, 46, 26, 13), c));
const barBg = () => S.svg(48, 28, S.path(rrect(1, 1, 46, 26, 13), '#3b2d22', { opacity: 0.55 }));
const pill = () => S.svg(96, 64, S.path(rrect(2, 2, 92, 60, 30), '#fffaf0', { stroke: '#d8b98a', strokeWidth: 4, opacity: 0.96 }));
const round = (down) => S.svg(96, 96, (down ? '' : circle(48, 52, 42, '#9a7a55')) +
  circle(48, down ? 52 : 46, 42, '#fff6e3', { stroke: '#c9a46e', strokeWidth: 4 }) + ellipse(48, down ? 34 : 28, 26, 12, '#ffffff', { opacity: 0.5 }));
const badge = () => S.svg(40, 40, circle(20, 20, 18, '#e2493b', { stroke: '#ffffff', strokeWidth: 4 }));
const white = () => S.svg(8, 8, rect(0, 0, 8, 8, '#ffffff'));
const knob = () => S.svg(16, 48, S.path(rrect(2, 2, 12, 44, 6), '#c9a46e'));
const tabOn = () => S.svg(96, 64, S.path(rrect(2, 2, 92, 60, 22), '#6aa84f', { stroke: '#4f8a3a', strokeWidth: 3 }));
const tabOff = () => S.svg(96, 64, S.path(rrect(2, 2, 92, 60, 22), '#ead7b4', { stroke: '#d1b68a', strokeWidth: 3 }));
const vignette = () => S.svg(256, 256, rect(0, 0, 256, 256, 'url(#vg)'), S.radial('vg', [[0.55, '#000000', 0], [1, '#000000', 0.45]]));

function logo() {
  const font = fs.readFileSync(path.join(__dirname, '../../../assets/fonts/Nunito-ExtraBold.ttf')).toString('base64');
  const style = `<style>@font-face{font-family:N;src:url(data:font/ttf;base64,${font})}text{font-family:N;font-weight:800}</style>`;
  const W = 900, H = 260;
  let d = '';
  const t = (fill, extra) => `<text x="${W / 2}" y="190" text-anchor="middle" font-size="170" fill="${fill}" ${extra}>Dewfield</text>`;
  d += t('#3e6b2c', 'stroke="#3e6b2c" stroke-width="34" stroke-linejoin="round" transform="translate(0,10)"');
  d += t('#ffffff', 'stroke="#ffffff" stroke-width="26" stroke-linejoin="round"');
  d += t('url(#lg)', '');
  // A sprout on the "i".
  d += S.path('M600,52 Q584,20 556,18 Q562,50 600,58 Z', '#6cbf4f', { stroke: '#3e6b2c', strokeWidth: 5 });
  d += S.path('M604,56 Q626,22 656,24 Q644,56 604,62 Z', '#82cf63', { stroke: '#3e6b2c', strokeWidth: 5 });
  return S.svg(W, H, style + d, S.linear('lg', [[0, '#8fd16a'], [1, '#4f9a3e']]));
}

module.exports = function uiSprites() {
  const out = [];
  const add = (n, svg) => out.push({ name: 'ui/' + n, svg });
  add('panel', panel());
  add('panel_inset', panelInset());
  add('dark', dark());
  add('header', header());
  for (const [n, c] of [['green', '#6fbf4f'], ['orange', '#f2a03d'], ['blue', '#5f9fd6'], ['red', '#e2604b'], ['grey', '#bdb2a0'], ['cream', '#fff3d9']]) {
    add(`btn_${n}`, button(c, false));
    add(`btn_${n}_down`, button(c, true));
  }
  add('slot', slot(false));
  add('slot_sel', slot(true));
  add('bar_bg', barBg());
  add('bar_energy', bar('#ffcf3f'));
  add('bar_xp', bar('#a77bca'));
  add('bar_water', bar('#5fb6e8'));
  add('bar_green', bar('#6fbf4f'));
  add('pill', pill());
  add('round', round(false));
  add('round_down', round(true));
  add('badge', badge());
  add('white', white());
  add('knob', knob());
  add('tab_on', tabOn());
  add('tab_off', tabOff());
  add('vignette', vignette());
  add('logo', logo());
  return out;
};
