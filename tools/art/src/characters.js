// The farmer (three directions, walk and action frames), the four shopkeepers, and animals
// seen from the side (facing left; the game mirrors them to face right).
'use strict';
const S = require('./svg');
const { shade, rect, circle, ellipse, path, g, line, shadow } = S;

const ink = (c) => shade(c, -0.4);
const st = (c, w = 3) => ({ stroke: ink(c), strokeWidth: w, strokeLinejoin: 'round', strokeLinecap: 'round' });

const SKIN = '#f3c7a1';
const HAIR = '#6b4430';
const SHIRT = '#d9644a';
const OVERALL = '#4f7fc4';
const BOOT = '#6d4a33';
const STRAW = '#ecc66a';

// ------------------------------------------------------------------ farmer
// Canvas 128x176, feet at the bottom center. walk: 0 = stand, 1 and 2 = steps, 3 = action.

function legs(dir, frame) {
  const lift = frame === 1 ? [6, -4] : frame === 2 ? [-4, 6] : [0, 0];
  if (dir === 'side') {
    const swing = frame === 1 ? 9 : frame === 2 ? -9 : 0;
    return line(62, 134, 62 - swing, 160, OVERALL, 13) + line(66, 134, 66 + swing, 160, shade(OVERALL, -0.15), 13) +
      ellipse(60 - swing, 164, 10, 6, BOOT) + ellipse(68 + swing, 164, 10, 6, shade(BOOT, -0.15));
  }
  return line(52, 132, 52, 156 - lift[0], OVERALL, 14) + line(76, 132, 76, 156 - lift[1], OVERALL, 14) +
    ellipse(52, 160 - lift[0], 10, 7, BOOT) + ellipse(76, 160 - lift[1], 10, 7, BOOT);
}

function arms(dir, frame) {
  const sw = frame === 1 ? 6 : frame === 2 ? -6 : 0;
  if (frame === 3) {
    if (dir === 'down') return line(40, 104, 52, 130, SHIRT, 12) + line(88, 104, 76, 130, SHIRT, 12) + circle(54, 134, 7, SKIN) + circle(74, 134, 7, SKIN);
    if (dir === 'up') return line(40, 104, 50, 84, SHIRT, 12) + line(88, 104, 78, 84, SHIRT, 12);
    return line(60, 104, 34, 118, SHIRT, 12) + circle(30, 120, 7, SKIN);
  }
  if (dir === 'side') return line(64, 104, 64 + sw * 1.6, 130, SHIRT, 12) + circle(64 + sw * 1.8, 134, 7, SKIN);
  return line(38, 104, 36, 128 + sw, SHIRT, 12) + line(90, 104, 92, 128 - sw, SHIRT, 12) +
    circle(36, 132 + sw, 7, SKIN) + circle(92, 132 - sw, 7, SKIN);
}

function torso(dir) {
  if (dir === 'side') {
    return path('M46,98 Q46,90 56,90 H74 Q84,90 84,100 V136 H46 Z', SHIRT, st(SHIRT)) +
      path('M50,110 H82 V138 H50 Z', OVERALL, st(OVERALL));
  }
  let d = path('M38,100 Q38,90 50,90 H78 Q90,90 90,100 V136 H38 Z', SHIRT, st(SHIRT));
  d += path('M44,106 H84 V140 Q64,146 44,140 Z', OVERALL, st(OVERALL));
  d += line(48, 92, 50, 108, OVERALL, 6) + line(80, 92, 78, 108, OVERALL, 6);
  if (dir === 'down') d += rect(56, 114, 16, 12, shade(OVERALL, 0.15), { rx: 3 }) + circle(50, 108, 3, '#f2c94c') + circle(78, 108, 3, '#f2c94c');
  return d;
}

function head(dir) {
  let d = '';
  if (dir === 'down') {
    d += circle(64, 66, 30, SKIN, st(SKIN));
    d += path('M36,62 Q38,40 64,40 Q90,40 92,62 Q84,52 64,52 Q44,52 36,62 Z', HAIR);
    d += ellipse(53, 70, 4, 5, '#3a2a20') + ellipse(75, 70, 4, 5, '#3a2a20');
    d += circle(54, 68, 1.5, '#ffffff') + circle(76, 68, 1.5, '#ffffff');
    d += ellipse(46, 80, 6, 4, '#f29a8a', { opacity: 0.7 }) + ellipse(82, 80, 6, 4, '#f29a8a', { opacity: 0.7 });
    d += path('M58,82 Q64,87 70,82', 'none', { stroke: '#8a4b3a', strokeWidth: 3, strokeLinecap: 'round' });
  } else if (dir === 'up') {
    d += circle(64, 66, 30, SKIN, st(SKIN));
    d += path('M34,70 Q34,38 64,38 Q94,38 94,70 Q90,90 64,92 Q38,90 34,70 Z', HAIR, st(HAIR, 2));
  } else {
    d += circle(66, 66, 29, SKIN, st(SKIN));
    d += path('M64,38 Q94,38 95,66 Q94,86 82,92 Q86,72 76,60 Q66,52 46,56 Q50,40 64,38 Z', HAIR, st(HAIR, 2));
    d += ellipse(46, 70, 3.5, 5, '#3a2a20') + path('M38,74 q-4,3 0,6', 'none', { stroke: ink(SKIN), strokeWidth: 3, strokeLinecap: 'round' });
    d += ellipse(50, 80, 5, 3.5, '#f29a8a', { opacity: 0.7 });
  }
  // Straw hat.
  const hx = dir === 'side' ? 62 : 64;
  d += ellipse(hx, 48, 46, 14, STRAW, st(STRAW));
  d += path(`M${hx - 24},48 Q${hx - 24},22 ${hx},22 Q${hx + 24},22 ${hx + 24},48 Z`, STRAW, st(STRAW));
  d += path(`M${hx - 24},42 Q${hx},48 ${hx + 24},42 V48 Q${hx},54 ${hx - 24},48 Z`, '#d9644a');
  return d;
}

function farmer(dir, frame) {
  const bob = frame === 1 || frame === 2 ? -2 : 0;
  let d = shadow(64, 166, 30, 8, 0.25);
  const body = dir === 'up'
    ? arms(dir, frame) + legs(dir, frame) + torso(dir) + head(dir)
    : legs(dir, frame) + torso(dir) + arms(dir, frame) + head(dir);
  d += g(body, { transform: `translate(0,${bob})` });
  return S.svg(128, 176, d);
}

// ------------------------------------------------------------------ shopkeepers
// Upper bodies seen over a counter, 128x150, waist at the bottom.

function keeper(kind) {
  const P = {
    nargiz: { skin: '#e9b78f', top: '#5aa95b', apron: '#f6f1e3', hair: '#3b2a22' },
    orkhan: { skin: '#e3ad84', top: '#8a5a3c', apron: '#d9b56a', hair: '#2f2622' },
    leyla: { skin: '#f0c19c', top: '#ef8a3c', apron: '#fff4e2', hair: '#4a2c22' },
    tural: { skin: '#dba27b', top: '#5f7fa6', apron: '#8a5a3c', hair: '#3a2b24' },
  }[kind];
  let d = path('M24,150 Q24,96 64,96 Q104,96 104,150 Z', P.top, st(P.top));
  d += path('M40,150 V112 H88 V150 Z', P.apron, st(P.apron, 2));
  d += line(28, 120, 22, 146, P.top, 14) + line(100, 120, 106, 146, P.top, 14);
  d += circle(64, 66, 30, P.skin, st(P.skin));
  d += ellipse(53, 70, 4, 5, '#2e2018') + ellipse(75, 70, 4, 5, '#2e2018');
  d += circle(54, 68, 1.5, '#fff') + circle(76, 68, 1.5, '#fff');
  d += path('M57,82 Q64,88 71,82', 'none', { stroke: '#7a3f30', strokeWidth: 3, strokeLinecap: 'round' });
  d += ellipse(46, 80, 6, 4, '#f29a8a', { opacity: 0.6 }) + ellipse(82, 80, 6, 4, '#f29a8a', { opacity: 0.6 });
  if (kind === 'nargiz') {
    d += path('M30,72 Q28,30 64,30 Q100,30 98,72 Q96,52 64,48 Q32,52 30,72 Z', '#3f8a54', st('#3f8a54', 2));
    d += path('M30,72 Q24,100 40,106 L44,80 Z', '#3f8a54') + path('M98,72 Q104,100 88,106 L84,80 Z', '#3f8a54');
    for (let i = 0; i < 5; i++) d += circle(44 + i * 10, 40 + (i % 2) * 4, 3, '#f2c94c');
  } else if (kind === 'orkhan') {
    d += path('M34,58 Q34,32 64,32 Q94,32 94,58 Z', '#6d4a33', st('#6d4a33', 2)) + ellipse(64, 58, 36, 7, '#5b3d29');
    d += path('M50,79 Q57,74 64,79 Q71,74 78,79 Q71,84 64,80 Q57,84 50,79 Z', P.hair);
  } else if (kind === 'leyla') {
    d += path('M34,70 Q30,34 64,34 Q98,34 94,70 Q90,48 64,46 Q40,48 34,70 Z', P.hair, st(P.hair, 2));
    d += circle(64, 30, 14, P.hair) + rect(52, 38, 24, 8, '#f2c94c', { rx: 4 });
  } else {
    d += path('M34,64 Q32,34 64,34 Q96,34 94,64 Z', '#d9644a', st('#d9644a', 2)) + rect(30, 56, 68, 10, '#b24c37', { rx: 5 });
    d += path('M40,76 Q42,104 64,106 Q86,104 88,76 Q80,92 64,92 Q48,92 40,76 Z', P.hair);
    d += path('M56,84 Q64,90 72,84', 'none', { stroke: '#f0d0b8', strokeWidth: 3, strokeLinecap: 'round' });
  }
  return S.svg(128, 150, d);
}

// ------------------------------------------------------------------ animals (facing left)

function chicken(frame, brown) {
  const body = brown ? '#d8a065' : '#fbf7ee';
  const wing = brown ? '#c18650' : '#ece4d4';
  const peck = frame === 3;
  const step = frame === 1 ? 4 : frame === 2 ? -4 : 0;
  let d = shadow(50, 88, 28, 6, 0.22);
  d += line(44 + step, 72, 42 + step, 88, '#e89a3c', 4) + line(58 - step, 72, 60 - step, 88, '#e89a3c', 4);
  d += path('M70,52 Q92,34 88,62 Q84,70 74,70 Z', wing, st(body, 2.5));
  d += ellipse(54, 60, 28, 20, body, st(body, 3));
  d += path('M48,58 Q60,50 70,60 Q60,70 48,64 Z', wing);
  const hx = peck ? 28 : 30, hy = peck ? 66 : 38;
  d += circle(hx, hy, 13, body, st(body, 3));
  d += path(`M${hx - 6},${hy - 12} q3,-8 6,-2 q3,-8 6,0 q4,-6 5,3 z`, '#e2493b');
  d += path(`M${hx - 12},${hy - 2} l-10,4 l10,3 z`, '#f2a33a');
  d += ellipse(hx - 9, hy + 8, 3, 5, '#e2493b');
  d += circle(hx - 4, hy - 3, 2.5, '#2b2b2b');
  return S.svg(96, 96, d);
}

function cow(frame) {
  const W = 224, H = 176;
  const step = frame === 1 ? 6 : frame === 2 ? -6 : 0;
  const eat = frame === 3;
  let d = shadow(112, H - 10, 90, 12, 0.22);
  const leg = (x, s) => line(x + s, 118, x + s * 1.4, H - 22, '#f4f0e8', 16) + ellipse(x + s * 1.4, H - 16, 9, 6, '#4a3a33');
  d += leg(70, step) + leg(160, -step);
  d += path('M184,70 Q206,90 200,120', 'none', { stroke: '#f4f0e8', strokeWidth: 6, strokeLinecap: 'round' }) + circle(200, 122, 7, '#4a3a33');
  d += path('M52,62 Q52,44 80,44 H170 Q194,44 194,70 V108 Q194,128 170,128 H80 Q52,128 52,108 Z', '#f8f5ee', st('#f8f5ee', 3.5));
  d += path('M96,46 Q120,60 108,84 Q90,96 80,74 Q76,56 96,46 Z', '#3b3330') + path('M150,90 Q170,82 178,104 Q168,124 148,116 Q140,100 150,90 Z', '#3b3330');
  d += ellipse(140, 128, 16, 8, '#f3a6b0');
  d += leg(90, -step) + leg(178, step);
  const hx = 44, hy = eat ? 108 : 66;
  d += path(`M${hx + 14},${hy - 22} l-8,-12 l12,6 z M${hx + 30},${hy - 22} l8,-12 l-12,6 z`, '#efe0b8');
  d += ellipse(hx + 2, hy - 18, 13, 7, '#3b3330', { transform: `rotate(-20 ${hx + 2} ${hy - 18})` });
  d += ellipse(hx + 22, hy, 26, 28, '#f8f5ee', st('#f8f5ee', 3.5));
  d += ellipse(hx + 12, hy + 16, 22, 15, '#f3b3bb', st('#f3b3bb', 2.5));
  d += circle(hx + 4, hy + 16, 3, '#8a4b55') + circle(hx + 18, hy + 18, 3, '#8a4b55');
  d += circle(hx + 16, hy - 6, 3.5, '#2b2b2b');
  return S.svg(W, H, d);
}

function sheep(frame) {
  const W = 176, H = 144;
  const step = frame === 1 ? 5 : frame === 2 ? -5 : 0;
  const eat = frame === 3;
  let d = shadow(92, H - 10, 70, 10, 0.22);
  const leg = (x, s) => line(x + s, 96, x + s * 1.3, H - 20, '#4d4643', 10);
  d += leg(62, step) + leg(128, -step);
  const r = S.rng(42);
  let wool = '';
  for (let i = 0; i < 11; i++) {
    const a = (i / 11) * Math.PI * 2;
    wool += circle(98 + Math.cos(a) * 44, 72 + Math.sin(a) * 26, 20 + r() * 5, '#fbf6ea');
  }
  d += g(wool, { stroke: '#d9cfbb', strokeWidth: 3 }) + ellipse(98, 72, 48, 28, '#fbf6ea');
  d += circle(84, 62, 9, '#ffffff', { opacity: 0.8 });
  d += leg(76, -step) + leg(142, step);
  const hx = 40, hy = eat ? 92 : 60;
  d += ellipse(hx + 4, hy - 14, 12, 6, '#4d4643', { transform: `rotate(-25 ${hx + 4} ${hy - 14})` });
  d += ellipse(hx + 14, hy + 4, 18, 22, '#4d4643', st('#4d4643', 2));
  d += circle(hx + 18, hy - 14, 12, '#fbf6ea');
  d += circle(hx + 8, hy, 3, '#ffffff');
  return S.svg(W, H, d);
}

module.exports = function characters() {
  const out = [];
  for (const dir of ['down', 'up', 'side']) for (let f = 0; f < 4; f++) out.push({ name: `char/farmer_${dir}_${f}`, svg: farmer(dir, f) });
  for (const k of ['nargiz', 'orkhan', 'leyla', 'tural']) out.push({ name: `char/${k}`, svg: keeper(k) });
  for (let f = 0; f < 4; f++) {
    out.push({ name: `animal/chicken_${f}`, svg: chicken(f, false) });
    out.push({ name: `animal/chicken_brown_${f}`, svg: chicken(f, true) });
    out.push({ name: `animal/cow_${f}`, svg: cow(f) });
    out.push({ name: `animal/sheep_${f}`, svg: sheep(f) });
  }
  return out;
};
