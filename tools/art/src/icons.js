// 96x96 icons: every item (seeds come in packets), animals, machines, upgrades and the HUD.
'use strict';
const S = require('./svg');
const { shade, rect, circle, ellipse, path, g, line, polygon } = S;
const { ITEMS, drawItem } = require('./produce');

const ink = (c) => shade(c, -0.4);
const st = (c, w = 3) => ({ stroke: ink(c), strokeWidth: w, strokeLinejoin: 'round', strokeLinecap: 'round' });
const N = 96;

const SEED_COLORS = {
  radish: '#f3b8c4', lettuce: '#cfeab8', wheat: '#f3e1a6', clover: '#c8e6b5', carrot: '#ffd3a8',
  potato: '#e8d3b0', tomato: '#ffc2b8', corn: '#fbe6a0', strawberry: '#ffc6cf', pumpkin: '#ffd2a1',
};

function packet(crop) {
  const c = SEED_COLORS[crop];
  let d = path('M18,20 L22,88 H74 L78,20 Z', c, st(c));
  d += path('M18,20 L26,10 H70 L78,20 Z', shade(c, -0.12), st(c, 2.5));
  d += rect(24, 64, 48, 16, '#fffaf0', { rx: 4, opacity: 0.85 });
  for (let i = 0; i < 3; i++) d += ellipse(36 + i * 12, 72, 3, 4, '#8a6a3c');
  d += drawItem(crop, 48, 42, 0.42);
  return S.svg(N, N, d);
}

function item(id) {
  return S.svg(N, N, drawItem(id, 48, 50, 1.0));
}

// Animal heads for the livestock shop.
const animalIcons = {
  chicken: () => circle(48, 52, 30, '#fbf7ee', st('#e2d8c4')) + path('M38,24 q4,-10 8,-2 q4,-10 8,0 q5,-8 6,4 z', '#e2493b') +
    path('M18,52 l-12,5 l12,4 z', '#f2a33a') + ellipse(26, 66, 4, 7, '#e2493b') + circle(36, 46, 4, '#2b2b2b'),
  cow: () => ellipse(48, 46, 32, 30, '#f8f5ee', st('#d9d2c4')) + path('M30,28 Q44,22 46,40 Q36,48 28,40 Z', '#3b3330') +
    ellipse(48, 66, 26, 16, '#f3b3bb', st('#f3b3bb')) + circle(40, 66, 3, '#8a4b55') + circle(56, 66, 3, '#8a4b55') +
    circle(36, 44, 4, '#2b2b2b') + circle(60, 44, 4, '#2b2b2b') + path('M22,22 l-6,-10 l12,4 z M74,22 l6,-10 l-12,4 z', '#efe0b8'),
  sheep: () => {
    let d = '';
    for (let i = 0; i < 9; i++) { const a = (i / 9) * Math.PI * 2; d += circle(48 + Math.cos(a) * 28, 44 + Math.sin(a) * 24, 14, '#fbf6ea'); }
    return g(d, { stroke: '#d9cfbb', strokeWidth: 2.5 }) + ellipse(48, 54, 18, 22, '#4d4643', st('#4d4643', 2)) + circle(42, 50, 3, '#fff') + circle(54, 50, 3, '#fff') + circle(48, 30, 12, '#fbf6ea');
  },
};

const machineIcons = {
  mill: () => rect(34, 40, 28, 46, '#eadfc8', st('#eadfc8')) + path('M30,42 Q48,20 66,42 Z', '#8a5a3c') +
    g([0, 90, 180, 270].map((a) => rect(45, 6, 6, 32, '#9a6538', { transform: `rotate(${a} 48 36)` })).join('') + circle(48, 36, 6, '#6f4730')),
  cheese_press: () => drawItem('cheese', 48, 58, 0.8) + rect(42, 8, 12, 30, '#8f8f8f') + rect(26, 6, 44, 8, '#9a6538', { rx: 4 }),
  loom: () => rect(14, 16, 8, 70, '#9a6538') + rect(74, 16, 8, 70, '#9a6538') + rect(10, 14, 76, 8, '#c98e5a', { rx: 3 }) +
    ['#d9644a', '#f2c94c', '#5f9fd6'].map((c, i) => rect(22, 40 + i * 12, 52, 12, c)).join(''),
};

const upgradeIcons = {
  can: () => canIcon(),
  field: () => rect(12, 18, 72, 64, '#9c6b3f', { rx: 12, ...st('#9c6b3f') }) + [32, 48, 64].map((y) => line(20, y, 76, y, '#7e5430', 4)).join('') +
    circle(70, 72, 16, '#5fae4e', st('#5fae4e')) + path('M70,64 v16 M62,72 h16', 'none', { stroke: '#fff', strokeWidth: 5, strokeLinecap: 'round' }),
  coop: () => rect(18, 40, 60, 44, '#dca46a', st('#dca46a')) + path('M10,44 L48,14 L86,44 Z', '#cf5a48', st('#cf5a48')) + path('M40,84 V64 Q40,56 48,56 Q56,56 56,64 V84 Z', '#7a4b2a'),
  barn: () => rect(16, 40, 64, 46, '#c8553d', st('#c8553d')) + path('M8,44 L20,24 L38,12 H58 L76,24 L88,44 Z', '#8d3d2f', st('#8d3d2f')) +
    rect(36, 56, 24, 30, '#f7f3ea') + line(36, 56, 60, 86, '#c8553d', 4) + line(60, 56, 36, 86, '#c8553d', 4),
};

function canIcon() {
  return path('M22,40 H62 V78 Q62,84 56,84 H28 Q22,84 22,78 Z', '#5f9fd6', st('#5f9fd6')) + path('M62,50 L84,30 L88,36 L64,60 Z', '#5f9fd6', st('#5f9fd6', 2.5)) +
    path('M30,40 Q42,18 54,40', 'none', { stroke: ink('#5f9fd6'), strokeWidth: 5 }) + ellipse(86, 32, 6, 4, '#3f7fb6');
}

const ui = {
  coin: () => circle(48, 48, 36, '#f6c945', st('#d9a52a', 4)) + circle(48, 48, 26, '#ffd95e') + path('M48,30 L53,43 H67 L56,51 L60,65 L48,57 L36,65 L40,51 L29,43 H43 Z', '#e8b13a'),
  energy: () => path('M54,8 L22,54 H44 L38,88 L74,38 H52 Z', '#ffd23f', st('#e0a92a', 4)),
  xp: () => path('M48,8 L59,36 L89,37 L65,56 L74,86 L48,68 L22,86 L31,56 L7,37 L37,36 Z', '#a77bca', st('#7d58a0', 4)),
  clock: () => circle(48, 48, 36, '#fffaf0', st('#c9b48f', 4)) + path('M48,26 V48 L62,58', 'none', { stroke: '#5b4a3a', strokeWidth: 6, strokeLinecap: 'round' }),
  sun: () => [...Array(8)].map((_, i) => line(48 + Math.cos(i * 0.785) * 30, 48 + Math.sin(i * 0.785) * 30, 48 + Math.cos(i * 0.785) * 42, 48 + Math.sin(i * 0.785) * 42, '#f6b733', 6)).join('') +
    circle(48, 48, 22, '#ffd23f', st('#f6b733', 3)),
  cloud: () => cloud('#ffffff'),
  rain: () => g(cloud('#e3eef7'), { transform: 'translate(0,-12)' }) + [30, 48, 66].map((x) => path(`M${x},64 l-5,14`, 'none', { stroke: '#4f9fd6', strokeWidth: 6, strokeLinecap: 'round' })).join(''),
  bag: () => path('M18,36 Q18,26 28,26 H68 Q78,26 78,36 V80 Q78,88 70,88 H26 Q18,88 18,80 Z', '#c98e5a', st('#c98e5a')) +
    path('M34,26 Q34,10 48,10 Q62,10 62,26', 'none', { stroke: ink('#c98e5a'), strokeWidth: 6 }) + rect(28, 46, 40, 22, '#dca46a', { rx: 6, ...st('#c98e5a', 2) }) + circle(48, 57, 4, '#f2c94c'),
  orders: () => rect(18, 14, 60, 76, '#fffaf0', { rx: 8, ...st('#c9b48f', 4) }) + rect(34, 8, 28, 14, '#9a6538', { rx: 5 }) +
    [36, 52, 68].map((y) => line(30, y, 66, y, '#c9b48f', 5)).join('') + path('M28,36 l3,3 l5,-6', 'none', { stroke: '#5fae4e', strokeWidth: 4, strokeLinecap: 'round' }),
  menu: () => [28, 48, 68].map((y) => line(22, y, 74, y, '#5b4a3a', 9)).join(''),
  close: () => path('M26,26 L70,70 M70,26 L26,70', 'none', { stroke: '#ffffff', strokeWidth: 12, strokeLinecap: 'round' }),
  check: () => path('M20,50 L40,70 L76,28', 'none', { stroke: '#ffffff', strokeWidth: 12, strokeLinecap: 'round', strokeLinejoin: 'round' }),
  lock: () => path('M32,44 V34 Q32,16 48,16 Q64,16 64,34 V44', 'none', { stroke: '#7a7a7a', strokeWidth: 9 }) + rect(22, 42, 52, 42, '#f2c94c', { rx: 10, ...st('#d9a52a', 3) }) + circle(48, 62, 6, '#8a6a20'),
  heart: () => path('M48,84 Q8,56 14,32 Q22,10 48,28 Q74,10 82,32 Q88,56 48,84 Z', '#f0566e', st('#c93c54', 4)) + ellipse(30, 32, 8, 5, '#ff9fb0', { transform: 'rotate(-30 30 32)' }),
  water: () => path('M48,10 Q78,48 76,62 Q74,86 48,86 Q22,86 20,62 Q18,48 48,10 Z', '#5fb6e8', st('#3f8fc2', 4)) + ellipse(36, 58, 6, 10, '#bfe6fb'),
  hoe: () => line(26, 84, 66, 24, '#b98552', 9) + path('M58,16 L84,24 L80,40 L62,30 Z', '#9aa3ab', st('#9aa3ab')),
  can: () => canIcon(),
  hand: () => path('M30,86 Q20,60 22,44 Q24,36 30,40 L34,54 V22 Q34,16 40,16 Q46,16 46,22 V46 V16 Q46,10 52,10 Q58,10 58,16 V46 V20 Q58,14 64,14 Q70,14 70,20 V50 V30 Q70,24 75,24 Q80,24 80,30 V58 Q80,86 54,88 Z', '#f3c7a1', st('#d9a07a', 3)),
  seed: () => ellipse(48, 52, 22, 30, '#c9995c', st('#a77c46')) + path('M48,26 V78', 'none', { stroke: '#a77c46', strokeWidth: 4 }),
  arrow_up: () => path('M48,18 L80,58 H60 V80 H36 V58 H16 Z', '#5fae4e', st('#3f8a34', 3)),
  arrow_down: () => path('M48,80 L80,40 H60 V18 H36 V40 H16 Z', '#e2493b', st('#b2382c', 3)),
  arrow_flat: () => rect(16, 38, 64, 20, '#b8ad9c', { rx: 10 }),
  star_silver: () => path('M48,8 L59,36 L89,37 L65,56 L74,86 L48,68 L22,86 L31,56 L7,37 L37,36 Z', '#d9dee4', st('#9aa3ab', 4)),
  star_gold: () => path('M48,8 L59,36 L89,37 L65,56 L74,86 L48,68 L22,86 L31,56 L7,37 L37,36 Z', '#ffd23f', st('#d9a52a', 4)),
  sound_on: () => path('M16,38 H32 L54,18 V78 L32,58 H16 Z', '#5b4a3a') + path('M64,32 Q76,48 64,64 M72,22 Q92,48 72,74', 'none', { stroke: '#5b4a3a', strokeWidth: 7, strokeLinecap: 'round' }),
  sound_off: () => path('M16,38 H32 L54,18 V78 L32,58 H16 Z', '#5b4a3a') + path('M64,36 L86,60 M86,36 L64,60', 'none', { stroke: '#e2493b', strokeWidth: 8, strokeLinecap: 'round' }),
  music: () => path('M36,74 V22 L76,12 V64', 'none', { stroke: '#5b4a3a', strokeWidth: 7, strokeLinejoin: 'round' }) + ellipse(28, 74, 12, 9, '#5b4a3a') + ellipse(68, 64, 12, 9, '#5b4a3a'),
  save: () => rect(16, 16, 64, 64, '#5f9fd6', { rx: 8, ...st('#5f9fd6') }) + rect(28, 16, 40, 22, '#e6f1fb') + rect(26, 50, 44, 30, '#ffffff', { rx: 3 }),
  home: () => path('M14,48 L48,18 L82,48 V82 H58 V60 H38 V82 H14 Z', '#d9644a', st('#b24c37')),
  play: () => path('M30,18 L78,48 L30,78 Z', '#ffffff'),
  info: () => circle(48, 48, 36, '#5f9fd6', st('#3f7fb6', 4)) + circle(48, 28, 6, '#ffffff') + rect(42, 40, 12, 32, '#ffffff', { rx: 5 }),
  plus: () => path('M48,20 V76 M20,48 H76', 'none', { stroke: '#ffffff', strokeWidth: 12, strokeLinecap: 'round' }),
  minus: () => path('M20,48 H76', 'none', { stroke: '#ffffff', strokeWidth: 12, strokeLinecap: 'round' }),
  globe: () => circle(48, 48, 34, '#8fd0f0', st('#4f9fd6', 4)) + path('M48,14 Q30,48 48,82 M48,14 Q66,48 48,82 M14,48 H82', 'none', { stroke: '#4f9fd6', strokeWidth: 4 }),
  basket: () => path('M14,44 H82 L74,84 H22 Z', '#c98e5a', st('#c98e5a')) + path('M28,44 Q48,6 68,44', 'none', { stroke: ink('#c98e5a'), strokeWidth: 6 }) +
    circle(36, 42, 9, '#fbf3e3') + circle(52, 40, 9, '#fbf3e3') + circle(62, 44, 8, '#f2c94c'),
  pin: () => path('M48,88 Q20,52 20,36 Q20,10 48,10 Q76,10 76,36 Q76,52 48,88 Z', '#e2493b', st('#b2382c', 3)) + circle(48, 36, 11, '#ffffff'),
  queue: () => [24, 48, 72].map((y, i) => circle(20, y, 7, '#ffffff') + line(36, y, 78, y, '#ffffff', 8)).join(''),
  zzz: () => path('M18,26 H44 L18,52 H44 M52,48 H72 L52,68 H72', 'none', { stroke: '#ffffff', strokeWidth: 7, strokeLinecap: 'round', strokeLinejoin: 'round' }),
};

function cloud(c) {
  return path('M22,70 Q8,70 8,56 Q8,42 24,42 Q26,22 46,22 Q62,22 68,36 Q88,34 88,54 Q88,70 72,70 Z', c, st('#b8c6d2', 4));
}

module.exports = function icons() {
  const out = [];
  for (const id of Object.keys(ITEMS)) out.push({ name: `icon/${id}`, svg: item(id) });
  for (const crop of Object.keys(SEED_COLORS)) out.push({ name: `icon/${crop}_seeds`, svg: packet(crop) });
  for (const [k, f] of Object.entries(animalIcons)) out.push({ name: `icon/animal_${k}`, svg: S.svg(N, N, f()) });
  for (const [k, f] of Object.entries(machineIcons)) out.push({ name: `icon/machine_${k}`, svg: S.svg(N, N, f()) });
  for (const [k, f] of Object.entries(upgradeIcons)) out.push({ name: `icon/upgrade_${k}`, svg: S.svg(N, N, f()) });
  for (const [k, f] of Object.entries(ui)) out.push({ name: `icon/${k}`, svg: S.svg(N, N, f()) });
  return out;
};
