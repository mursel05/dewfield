// Crop growth stages: 0 sprout, 1 young, 2 grown, 3 ripe, plus a withered plant.
// Canvas 128x176 (corn 128x224); the stem base sits 40px above the bottom edge.
'use strict';
const S = require('./svg');
const { shade, rect, circle, ellipse, path, g, line } = S;
const { drawItem } = require('./produce');

const ink = (c) => shade(c, -0.4);
const st = (c, w = 2.5) => ({ stroke: ink(c), strokeWidth: w, strokeLinejoin: 'round', strokeLinecap: 'round' });
const W = 128;

function leaf(x, y, len, angle, color, width = 0.45) {
  const a = (angle * Math.PI) / 180;
  const ex = x + Math.cos(a) * len, ey = y + Math.sin(a) * len;
  const nx = -Math.sin(a) * len * width, ny = Math.cos(a) * len * width;
  const mx = (x + ex) / 2, my = (y + ey) / 2;
  return path(`M${x},${y} Q${mx + nx},${my + ny} ${ex},${ey} Q${mx - nx},${my - ny} ${x},${y} Z`, color, st(color));
}

function fan(x, y, n, len, spread, color, width) {
  let d = '';
  for (let i = 0; i < n; i++) {
    const ang = -90 + (i - (n - 1) / 2) * spread;
    d += leaf(x, y, len * (0.85 + ((i * 37) % 10) / 40), ang, i % 2 ? color : shade(color, -0.1), width);
  }
  return d;
}

const sprout = (H, color = '#6cbf4f') => {
  const b = H - 40;
  return line(64, b, 64, b - 14, '#5a9e43', 3) + leaf(64, b - 12, 18, -150, color) + leaf(64, b - 12, 18, -30, color);
};

const moundShadow = (H) => ellipse(64, H - 38, 30, 7, '#3b2a1a', { opacity: 0.18 });

const CROPS = {
  radish: { color: '#5fae4e', stages: [
    (H) => fan(64, H - 40, 3, 24, 40, '#5fae4e', 0.5),
    (H) => fan(64, H - 40, 5, 34, 30, '#5fae4e', 0.5),
    (H) => drawItem('radish', 64, H - 44, 0.55) + fan(64, H - 52, 5, 40, 30, '#5fae4e', 0.5),
  ] },
  lettuce: { color: '#7cc45f', stages: [
    (H) => fan(64, H - 40, 4, 18, 45, '#8fd16f', 0.6),
    (H) => circle(64, H - 54, 20, '#7cc45f', st('#7cc45f')) + circle(64, H - 54, 11, '#9fdc7c'),
    (H) => drawItem('lettuce', 64, H - 58, 0.9),
  ] },
  wheat: { color: '#8bc34a', stages: [
    (H) => stalks(H, 4, 34, '#7cbf55', false),
    (H) => stalks(H, 6, 60, '#86c45a', false),
    (H) => stalks(H, 7, 76, '#e1bd55', true),
  ] },
  clover: { color: '#5fae4e', stages: [
    (H) => trefoils(H, 3, false),
    (H) => trefoils(H, 5, false),
    (H) => trefoils(H, 6, true),
  ] },
  carrot: { color: '#6cbf4f', stages: [
    (H) => feather(H, 3, 26),
    (H) => feather(H, 5, 40),
    (H) => path(`M52,${H - 44} Q64,${H - 50} 76,${H - 44} L66,${H - 26} H62 Z`, '#f08a2c', st('#f08a2c')) + feather(H, 6, 46, H - 48),
  ] },
  potato: { color: '#5a9e43', stages: [
    (H) => bushy(H, 22, '#5fae4e'),
    (H) => bushy(H, 34, '#5a9e43'),
    (H) => bushy(H, 40, '#5a9e43') + [[44, 70], [80, 64], [62, 82]].map(([x, y]) => flower(x, H - y, '#f3e9ff', '#ffd54f')).join('') +
      ellipse(48, H - 36, 12, 8, '#c9995c', st('#c9995c')),
  ] },
  tomato: { color: '#4f9a42', stages: [
    (H) => stake(H, 40) + fan(64, H - 40, 3, 22, 50, '#5fae4e', 0.55),
    (H) => stake(H, 96) + vine(H, 70, false),
    (H) => stake(H, 106) + vine(H, 92, true),
  ], grown: (H) => stake(H, 106) + vine(H, 92, false) },
  corn: { color: '#6fb653', tall: true, stages: [
    (H) => fan(64, H - 40, 3, 30, 35, '#6fb653', 0.3),
    (H) => cornStalk(H, 110, false),
    (H) => cornStalk(H, 160, true),
  ], grown: (H) => cornStalk(H, 160, false) },
  strawberry: { color: '#4f9a42', stages: [
    (H) => trefoils(H, 2, false, '#4f9a42'),
    (H) => trefoils(H, 4, false, '#4f9a42') + flower(84, H - 58, '#ffffff', '#ffd54f'),
    (H) => trefoils(H, 5, false, '#4f9a42') + drawItem('strawberry', 40, H - 44, 0.36) + drawItem('strawberry', 70, H - 40, 0.42) + drawItem('strawberry', 92, H - 52, 0.34),
  ], grown: (H) => trefoils(H, 5, false, '#4f9a42') + flower(44, H - 60, '#ffffff', '#ffd54f') + flower(86, H - 56, '#ffffff', '#ffd54f') },
  pumpkin: { color: '#5fa846', stages: [
    (H) => vineLeaves(H, 2),
    (H) => vineLeaves(H, 4) + circle(80, H - 46, 9, '#f6c945', st('#f6c945')),
    (H) => vineLeaves(H, 4) + drawItem('pumpkin', 66, H - 54, 0.8),
  ] },
};

function stalks(H, n, h, color, ripe) {
  let d = '';
  for (let i = 0; i < n; i++) {
    const x = 34 + (i * 60) / (n - 1);
    const top = H - 40 - h + ((i * 13) % 12);
    d += path(`M${x},${H - 40} Q${x + (i % 2 ? 4 : -4)},${(H - 40 + top) / 2} ${x + (i - n / 2) * 1.5},${top}`, 'none', { stroke: color, strokeWidth: 4, strokeLinecap: 'round' });
    if (ripe) for (let k = 0; k < 4; k++) d += ellipse(x + (i - n / 2) * 1.5 + (k % 2 ? 4 : -4), top + 6 + k * 7, 4, 7, '#e7c35c', st('#e7c35c', 1.5));
  }
  return d;
}

function trefoils(H, n, flowers, color = '#5fae4e') {
  let d = '';
  const pos = [[64, 56], [42, 50], [86, 50], [52, 70], [78, 72], [64, 82]];
  for (let i = 0; i < n; i++) {
    const [x, y] = pos[i];
    d += line(x, H - 40, x, H - y, '#4f9a42', 3);
    for (let k = 0; k < 3; k++) {
      const a = -Math.PI / 2 + (k - 1) * 2.1;
      d += circle(x + Math.cos(a) * 8, H - y + Math.sin(a) * 8, 8, k === 1 ? shade(color, 0.12) : color, st(color, 2));
    }
    if (flowers && i % 2 === 0) d += circle(x, H - y - 16, 7, '#f7b6d2', st('#f7b6d2', 2));
  }
  return d;
}

function feather(H, n, len, base = H - 40) {
  let d = '';
  for (let i = 0; i < n; i++) {
    const a = ((-90 + (i - (n - 1) / 2) * 22) * Math.PI) / 180;
    const ex = 64 + Math.cos(a) * len, ey = base + Math.sin(a) * len;
    d += line(64, base, ex, ey, '#5a9e43', 3);
    for (let k = 1; k < 4; k++) {
      const px = 64 + (ex - 64) * (k / 4), py = base + (ey - base) * (k / 4);
      d += circle(px - 5, py, 5, '#6cbf4f') + circle(px + 5, py - 2, 5, '#79c95b');
    }
  }
  return d;
}

function bushy(H, r, color) {
  const rr = S.rng(r);
  let d = moundShadow(H);
  for (let i = 0; i < 7; i++) {
    const a = Math.PI + (i / 6) * Math.PI;
    d += circle(64 + Math.cos(a) * r * 0.9, H - 46 + Math.sin(a) * r * 0.9, r * 0.5 + rr() * 6, i % 2 ? color : shade(color, 0.12), st(color, 2));
  }
  return d + circle(64, H - 50, r * 0.6, shade(color, 0.08));
}

function flower(x, y, petal, center) {
  let d = '';
  for (let k = 0; k < 5; k++) d += circle(x + Math.cos(k * 1.256) * 5, y + Math.sin(k * 1.256) * 5, 4.5, petal);
  return d + circle(x, y, 3, center);
}

function stake(H, h) {
  return rect(60, H - 40 - h, 7, h, '#b98552', st('#b98552', 2));
}

function vine(H, h, fruit) {
  let d = '';
  for (let i = 0; i < 5; i++) {
    const y = H - 44 - (i * h) / 5;
    d += leaf(64, y, 26, i % 2 ? -20 : -160, '#5fae4e');
  }
  if (fruit) for (const [x, y] of [[44, 50], [86, 66], [48, 86], [80, 104]]) d += drawItem('tomato', x, H - y, 0.36);
  return d;
}

function cornStalk(H, h, ripe) {
  let d = line(64, H - 40, 64, H - 40 - h, '#6fb653', 8);
  for (let i = 0; i < 5; i++) {
    const y = H - 52 - (i * h) / 6;
    d += leaf(64, y, 44 - i * 4, i % 2 ? -25 : -155, i % 2 ? '#6fb653' : '#5fa846', 0.22);
  }
  d += path(`M64,${H - 40 - h} l-10,-16 M64,${H - 40 - h} l0,-20 M64,${H - 40 - h} l10,-16`, 'none', { stroke: ripe ? '#d9b44a' : '#a8c86a', strokeWidth: 4, strokeLinecap: 'round' });
  if (ripe) d += g(require('./produce').ITEMS.corn(), { transform: `translate(84,${H - 40 - h * 0.55}) scale(0.5) rotate(18)` });
  return d;
}

function vineLeaves(H, n) {
  let d = path(`M20,${H - 40} Q64,${H - 30} 108,${H - 46}`, 'none', { stroke: '#5a9e43', strokeWidth: 5, strokeLinecap: 'round' });
  const pos = [[36, 52], [92, 56], [56, 64], [76, 46]];
  for (let i = 0; i < n; i++) {
    const [x, y] = pos[i];
    d += circle(x, H - y, 16, i % 2 ? '#5fa846' : '#6fb653', st('#5fa846'));
    d += line(x, H - y, x + 8, H - y - 8, shade('#5fa846', -0.2), 2);
  }
  return d;
}

function dead() {
  const H = 176;
  let d = moundShadow(H);
  d += path(`M64,${H - 40} Q60,${H - 70} 70,${H - 86}`, 'none', { stroke: '#8a6a3c', strokeWidth: 5, strokeLinecap: 'round' });
  d += leaf(66, H - 66, 26, 160, '#a88a52', 0.4) + leaf(68, H - 78, 22, 30, '#9b7c48', 0.4) + leaf(64, H - 52, 20, -200, '#b0925b', 0.4);
  return S.svg(W, H, d);
}

module.exports = function crops() {
  const out = [];
  for (const [id, def] of Object.entries(CROPS)) {
    const H = def.tall ? 224 : 176;
    out.push({ name: `crop/${id}_0`, svg: S.svg(W, H, sprout(H, def.color)) });
    def.stages.forEach((f, i) => out.push({ name: `crop/${id}_${i + 1}`, svg: S.svg(W, H, (i > 0 ? moundShadow(H) : '') + f(H)) }));
    // Regrowing crops show this between harvests (the plant without fruit).
    if (def.grown) out.push({ name: `crop/${id}_regrow`, svg: S.svg(W, H, moundShadow(H) + def.grown(H)) });
  }
  out.push({ name: 'crop/dead', svg: dead() });
  return out;
};
