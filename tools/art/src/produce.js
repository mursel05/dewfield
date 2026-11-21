// Drawings of every item, used by item icons (and seed packets). Each function draws
// the item centered on (0,0) at about 80px tall; callers translate and scale.
'use strict';
const S = require('./svg');
const { shade, rect, circle, ellipse, path, g, line, polygon } = S;

const ink = (c) => shade(c, -0.4);
const st = (c, w = 3) => ({ stroke: ink(c), strokeWidth: w, strokeLinejoin: 'round', strokeLinecap: 'round' });
const LEAF = '#5fae4e';

function leafy(n, spread, len, color = LEAF) {
  let d = '';
  for (let i = 0; i < n; i++) {
    const a = -Math.PI / 2 + (i - (n - 1) / 2) * spread;
    const x = Math.cos(a) * len, y = Math.sin(a) * len;
    d += path(`M0,0 Q${x * 0.3 - 10},${y * 0.6} ${x},${y} Q${x * 0.3 + 10},${y * 0.6} 0,0 Z`, i % 2 ? color : shade(color, -0.12), st(color, 2));
  }
  return d;
}

const ITEMS = {
  radish: () => g(leafy(3, 0.5, 34), { transform: 'translate(0,-14)' }) + path('M-22,0 Q-22,-20 0,-20 Q22,-20 22,0 Q20,22 0,34 Q-20,22 -22,0 Z', '#e8506a', st('#e8506a')) +
    path('M-10,-8 Q-6,-14 2,-14', 'none', { stroke: '#ffd1da', strokeWidth: 5, strokeLinecap: 'round' }) + line(0, 34, 0, 42, '#f3d8de', 3),
  lettuce: () => circle(0, 6, 34, '#7cc45f', st('#7cc45f')) + path('M-26,6 Q-14,-26 0,-28 Q14,-26 26,6', 'none', { stroke: '#5fa846', strokeWidth: 4 }) +
    circle(0, 8, 22, '#9fdc7c') + path('M-12,8 Q0,-12 12,8', 'none', { stroke: '#6fb653', strokeWidth: 3.5 }),
  wheat: () => {
    let d = '';
    for (const a of [-0.35, 0, 0.35]) {
      let s = line(0, 36, 0, -14, '#c9a24a', 4);
      for (let i = 0; i < 4; i++) s += ellipse(-6, -10 - i * 9, 5, 8, '#e7c35c', { transform: `rotate(-25 -6 ${-10 - i * 9})` }) + ellipse(6, -10 - i * 9, 5, 8, '#e7c35c', { transform: `rotate(25 6 ${-10 - i * 9})` });
      d += g(s, { transform: `rotate(${a * 57} 0 36)` });
    }
    return d + rect(-12, 18, 24, 8, '#d9644a', { rx: 3 });
  },
  clover: () => {
    let d = line(0, 36, 2, 0, '#4f9a42', 4);
    for (let k = 0; k < 3; k++) {
      const a = -Math.PI / 2 + (k - 1) * 2.1;
      d += circle(Math.cos(a) * 16, -6 + Math.sin(a) * 16, 15, k === 1 ? '#6cba55' : '#5fae4e', st('#5fae4e', 2));
    }
    return d + circle(0, -6, 5, '#f7a8c8');
  },
  carrot: () => g(leafy(3, 0.4, 30, '#6cbf4f'), { transform: 'translate(0,-18)' }) + path('M-16,-20 Q0,-28 16,-20 L2,40 Q0,44 -2,40 Z', '#f08a2c', st('#f08a2c')) +
    line(-8, -6, 0, -6, ink('#f08a2c'), 2.5) + line(0, 8, 8, 8, ink('#f08a2c'), 2.5) + line(-4, 22, 2, 22, ink('#f08a2c'), 2.5),
  potato: () => path('M-30,4 Q-30,-22 -2,-24 Q30,-24 32,0 Q34,26 2,28 Q-30,28 -30,4 Z', '#c9995c', st('#c9995c')) +
    circle(-10, -6, 3, '#9a7040') + circle(12, 6, 3, '#9a7040') + circle(0, 16, 2.5, '#9a7040') + ellipse(-8, -14, 9, 4, '#dfb880', { opacity: 0.8 }),
  tomato: () => circle(0, 6, 30, '#e5473a', st('#e5473a')) + path('M-14,-20 L0,-14 L14,-20 L8,-12 L16,-6 L0,-10 L-16,-6 L-8,-12 Z', '#4f9a42') +
    ellipse(-10, -4, 8, 5, '#ff8b7e', { opacity: 0.8 }),
  corn: () => path('M-14,-34 Q0,-42 14,-34 L12,30 Q0,38 -12,30 Z', '#f4cf4d', st('#e0b23a')) +
    path('M-12,36 Q-30,0 -10,-30 Q-6,10 0,40 Z', '#6fb653', st('#6fb653', 2)) + path('M12,36 Q30,0 10,-30 Q6,10 0,40 Z', '#5fa846', st('#5fa846', 2)) +
    (() => { let d = ''; for (let i = 0; i < 6; i++) d += line(-8, -26 + i * 9, 8, -26 + i * 9, '#e2b53a', 2); return d; })(),
  strawberry: () => path('M-26,-10 Q-26,-24 0,-22 Q26,-24 26,-10 Q22,22 0,34 Q-22,22 -26,-10 Z', '#e8384f', st('#e8384f')) +
    path('M-16,-20 L-4,-14 L0,-26 L4,-14 L16,-20 L8,-10 H-8 Z', '#4f9a42') +
    (() => { let d = ''; for (const [x, y] of [[-12, -4], [0, -2], [12, -4], [-6, 10], [6, 10], [0, 22]]) d += ellipse(x, y, 1.8, 2.6, '#ffe28a'); return d; })(),
  pumpkin: () => ellipse(0, 6, 38, 30, '#f08a2c', st('#f08a2c')) + ellipse(0, 6, 22, 30, '#f59a40', st('#f08a2c', 2)) + ellipse(0, 6, 8, 30, '#f08a2c', st('#f08a2c', 2)) +
    rect(-4, -32, 8, 14, '#6d8a3a', { rx: 3 }) + path('M4,-26 q14,-10 22,2', 'none', { stroke: '#5fa846', strokeWidth: 4, strokeLinecap: 'round' }),
  egg: () => path('M0,-34 Q26,-34 28,6 Q28,34 0,34 Q-28,34 -28,6 Q-26,-34 0,-34 Z', '#fbf3e3', st('#e8dcc5')) + ellipse(-10, -10, 7, 10, '#ffffff', { opacity: 0.9 }),
  milk: () => path('M-18,-18 L-12,-28 H12 L18,-18 V34 Q18,40 12,40 H-12 Q-18,40 -18,34 Z', '#ffffff', st('#d7d7d7')) +
    rect(-13, -40, 26, 12, '#5f9fd6', { rx: 3, ...st('#5f9fd6', 2) }) + rect(-18, 2, 36, 18, '#5f9fd6') + ellipse(-8, -6, 4, 9, '#eef6ff'),
  wool: () => circle(0, 4, 32, '#f6efe0', st('#d9cfbb')) +
    path('M-26,-8 Q0,-30 26,-8 M-30,8 Q0,-14 30,8 M-24,22 Q0,4 24,22', 'none', { stroke: '#e0d3bb', strokeWidth: 4, strokeLinecap: 'round' }) +
    path('M24,24 q10,8 18,4', 'none', { stroke: '#e0d3bb', strokeWidth: 4, strokeLinecap: 'round' }),
  cheese: () => path('M-34,22 L-30,-10 L30,-28 L34,22 Z', '#f6c945', st('#e0a92a')) + path('M-30,-10 L30,-28 L34,-12 L-34,0 Z', '#ffe07a') +
    circle(-14, 10, 5, '#e0a92a') + circle(10, 2, 6, '#e0a92a') + circle(20, 16, 4, '#e0a92a'),
  cloth: () => rect(-34, -22, 68, 48, '#c0453a', { rx: 8, ...st('#c0453a') }) + rect(-34, -6, 68, 10, '#f2c94c') + rect(-34, 12, 68, 6, '#1f4e79') +
    ellipse(34, 2, 8, 24, '#a63a34', st('#a63a34', 2)),
  grain: () => sack('#e8d3a6', '#e5c060', 'grain'),
  hay: () => rect(-36, -22, 72, 48, '#e8c766', { rx: 10, ...st('#c9a43f') }) + rect(-14, -22, 8, 48, '#b07b4a') + rect(10, -22, 8, 48, '#b07b4a') +
    (() => { let d = ''; const r = S.rng(3); for (let i = 0; i < 10; i++) { const x = -30 + r() * 60, y = -16 + r() * 36; d += line(x, y, x + 8, y - 4, '#c9a43f', 2); } return d; })(),
  clover_mix: () => sack('#cfe3b5', '#6cba55', 'clover'),
  fertilizer: () => sack('#efe7da', '#8a5a34', 'sprout'),
  fried_egg: () => ellipse(0, 10, 40, 22, '#ffffff', st('#d8d8d8')) + path('M-24,6 Q-26,-10 -6,-8 Q8,-18 20,-4 Q30,10 10,16 Q-20,22 -24,6 Z', '#fffaf0', st('#ece3d2', 2)) + circle(-2, 2, 10, '#f6b733'),
  salad: () => bowl('#7cc45f', [['#e5473a', -12, -8], ['#9fdc7c', 8, -12], ['#e5473a', 14, -4]]),
  veggie_stew: () => bowl('#b8743c', [['#f08a2c', -10, -6], ['#c9995c', 8, -8], ['#6fb653', 2, -14]]),
  pancakes: () => ellipse(0, 26, 40, 12, '#ffffff', st('#d8d8d8')) + [14, 4, -6].map((y) => ellipse(0, y, 30, 10, '#e7b062', st('#c98e3a', 2))).join('') +
    path('M-18,-10 Q0,-18 18,-10 L14,0 Q0,4 -12,-2 Z', '#a5602c') + rect(-6, -22, 12, 9, '#fff3b0', { rx: 2 }),
  pumpkin_soup: () => bowl('#f2903c', [['#ffffff', 0, -8]]),
};

function sack(bag, fill, mark) {
  let d = path('M-28,36 Q-34,-4 -20,-24 H20 Q34,-4 28,36 Z', bag, st(bag));
  d += path('M-20,-24 Q0,-36 20,-24 Q0,-16 -20,-24 Z', fill);
  d += rect(-24, -28, 48, 8, ink(bag), { rx: 4, opacity: 0.6 });
  if (mark === 'grain') for (let i = 0; i < 5; i++) d += ellipse(-12 + i * 6, 10 + (i % 2) * 6, 3, 5, '#d4ad48');
  if (mark === 'clover') for (let k = 0; k < 3; k++) d += circle(Math.cos(k * 2.1 - 1.57) * 9, 10 + Math.sin(k * 2.1 - 1.57) * 9, 8, '#5fae4e');
  if (mark === 'sprout') d += path('M0,26 V6 M0,10 Q-14,0 -16,-6 Q-2,-6 0,8 M0,8 Q12,-4 16,-8 Q16,4 0,10', 'none', { stroke: '#5fae4e', strokeWidth: 5, strokeLinecap: 'round' });
  return d;
}

function bowl(soup, bits) {
  let d = path('M-38,-4 H38 Q36,34 0,36 Q-36,34 -38,-4 Z', '#f1ece2', st('#d5cdbd'));
  d += ellipse(0, -4, 38, 12, soup, st(soup, 2));
  for (const [c, x, y] of bits) d += circle(x, y + 2, 7, c);
  return d;
}

/** Item drawing centered at (cx, cy) scaled. */
function drawItem(id, cx, cy, scale = 1) {
  const f = ITEMS[id];
  if (!f) throw new Error('no drawing for ' + id);
  return g(f(), { transform: `translate(${cx},${cy}) scale(${scale})` });
}

module.exports = { ITEMS, drawItem, LEAF };
