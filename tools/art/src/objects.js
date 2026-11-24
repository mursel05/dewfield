// Buildings, furniture, props and effects. Every sprite's bottom edge is the bottom of the
// object's footprint, and its width is the footprint width unless noted (props are centered).
'use strict';
const S = require('./svg');
const { TILE: T, shade, rect, circle, ellipse, path, g, line, polygon, shadow, rrect, blob } = S;

const ink = (c) => shade(c, -0.38);
const st = (c, w = 3) => ({ stroke: ink(c), strokeWidth: w, strokeLinejoin: 'round' });

const C = {
  wood: '#c98e5a', woodDark: '#9a6538', woodLight: '#e2ad76',
  cream: '#f4e8d2', roof: '#d9644a', roofDark: '#b24c37',
  stone: '#bdb6a8', stoneLight: '#d8d2c5', glass: '#a9dcf2', leaf: '#62ad50', leafDark: '#4a9142', leafLight: '#82c867',
  red: '#c8553d', white: '#f7f3ea', slate: '#5f7fa6', gold: '#e8c35a',
};

function planks(x, y, w, h, color, n, vertical = false) {
  let d = '';
  for (let i = 1; i < n; i++) {
    if (vertical) d += line(x + (w / n) * i, y + 4, x + (w / n) * i, y + h - 4, shade(color, -0.18), 2.5);
    else d += line(x + 4, y + (h / n) * i, x + w - 4, y + (h / n) * i, shade(color, -0.18), 2.5);
  }
  return d;
}

function windowBox(x, y, w, h, flowers = true) {
  let d = rect(x - 6, y - 6, w + 12, h + 12, C.white, { rx: 6, ...st(C.white, 2.5) });
  d += rect(x, y, w, h, C.glass, { rx: 3 });
  d += path(`M${x + 6},${y + h - 10} L${x + w * 0.45},${y + 8}`, 'none', { stroke: '#e3f5fd', strokeWidth: 7, strokeLinecap: 'round', opacity: 0.8 });
  d += rect(x + w / 2 - 3, y, 6, h, C.white) + rect(x, y + h / 2 - 3, w, 6, C.white);
  if (flowers) {
    d += rect(x - 8, y + h + 4, w + 16, 16, C.woodDark, { rx: 4 });
    const r = S.rng(x * 3 + y);
    for (let i = 0; i < 6; i++) {
      const fx = x - 2 + (i + 0.5) * ((w + 4) / 6);
      d += circle(fx, y + h + 2, 9, C.leaf);
      d += circle(fx + (r() - 0.5) * 6, y + h - 2, 5, ['#f06f8f', '#ffd54f', '#ff8a65', '#ffffff'][i % 4]);
    }
  }
  return d;
}

// ------------------------------------------------------------------ buildings

function house() {
  const W = 5 * T, H = Math.round(4.7 * T);
  let d = shadow(W / 2, H - 14, 300, 22, 0.25);
  // Walls with timber frame.
  d += rect(40, 290, W - 80, H - 300, C.cream, st(C.cream));
  d += rect(40, 290, 18, H - 300, C.woodDark) + rect(W - 58, 290, 18, H - 300, C.woodDark);
  d += rect(40, 430, W - 80, 14, C.woodDark);
  d += rect(40, 290, W - 80, 26, '#000', { opacity: 0.12 });
  // Windows and door.
  d += windowBox(108, 340, 96, 80) + windowBox(W - 204, 340, 96, 80);
  d += rect(244, H - 22, 152, 18, C.stoneLight, { rx: 6, ...st(C.stoneLight, 2) });
  d += path(`M268,${H - 22} V470 Q268,430 320,430 Q372,430 372,470 V${H - 22} Z`, C.woodDark, st(C.woodDark));
  d += planks(268, 440, 104, H - 462, C.woodDark, 4, true);
  d += circle(356, 520, 6, C.gold);
  // Roof: big front slope with shingle rows, ridge and chimney.
  d += rect(452, 30, 56, 120, '#b85a44', { rx: 4, ...st('#b85a44') }) + rect(444, 22, 72, 18, '#8f4636', { rx: 4 });
  d += circle(480, 10, 10, '#eeeeee', { opacity: 0.5 });
  d += polygon([[8, 306], [W - 8, 306], [W - 78, 92], [78, 92]], C.roof, st(C.roof, 4));
  for (let i = 1; i < 6; i++) {
    const y = 92 + i * 36;
    const inset = 78 - (70 * i * 36) / 214;
    d += path(`M${inset + 4},${y} H${W - inset - 4}`, 'none', { stroke: C.roofDark, strokeWidth: 3, opacity: 0.6 });
    for (let x = inset + 30 + (i % 2) * 22; x < W - inset - 20; x += 44) {
      d += line(x, y - 34, x, y - 4, C.roofDark, 2, { opacity: 0.35 });
    }
  }
  d += rect(70, 80, W - 140, 22, C.roofDark, { rx: 10 });
  d += path(`M14,300 L${W - 14},300`, 'none', { stroke: shade(C.roof, 0.25), strokeWidth: 6, strokeLinecap: 'round' });
  return S.svg(W, H, d);
}

function well() {
  const W = T, H = Math.round(1.8 * T);
  let d = shadow(64, H - 10, 56, 14);
  d += rect(16, 40, 10, 128, C.woodDark, st(C.woodDark, 2)) + rect(102, 40, 10, 128, C.woodDark, st(C.woodDark, 2));
  d += polygon([[2, 64], [126, 64], [100, 18], [28, 18]], C.roof, st(C.roof));
  d += rect(20, 72, 88, 7, C.woodDark);
  d += line(64, 76, 64, 112, '#8d7b65', 2.5);
  d += path('M52,110 h24 l-3,22 h-18 z', C.wood, st(C.wood, 2));
  d += path(`M12,150 V${H - 26} Q64,${H - 2} 116,${H - 26} V150 Z`, C.stone, st(C.stone));
  for (let i = 0; i < 3; i++) d += path(`M14,${166 + i * 18} Q64,${186 + i * 18} 114,${166 + i * 18}`, 'none', { stroke: shade(C.stone, -0.2), strokeWidth: 2 });
  d += ellipse(64, 150, 52, 20, C.stoneLight, st(C.stoneLight));
  d += ellipse(64, 151, 38, 12, '#3b7fa8');
  return S.svg(W, H, d);
}

function mill() {
  const W = 3 * T, H = Math.round(5.4 * T);
  let d = shadow(W / 2, H - 14, 170, 20, 0.25);
  d += polygon([[52, H - 10], [W - 52, H - 10], [W - 100, 200], [100, 200]], '#eadfc8', st('#eadfc8', 4));
  for (let i = 1; i < 9; i++) {
    const y = 200 + i * ((H - 210) / 9);
    const t = (y - 200) / (H - 210);
    const x0 = 100 - 48 * t, x1 = W - 100 + 48 * t;
    d += line(x0 + 6, y, x1 - 6, y, '#d3c4a6', 2.5);
  }
  d += path(`M164,${H - 10} V${H - 96} Q164,${H - 130} 192,${H - 130} Q220,${H - 130} 220,${H - 96} V${H - 10} Z`, C.woodDark, st(C.woodDark));
  d += rect(172, 360, 40, 48, C.glass, { rx: 18, ...st(C.white, 4) });
  d += path(`M84,214 Q192,90 300,214 Z`, '#8a5a3c', st('#8a5a3c', 4));
  d += rect(80, 204, 224, 18, '#6f4730', { rx: 8 });
  return S.svg(W, H, d);
}

function millSails() {
  const W = 440, cx = W / 2;
  let d = '';
  for (let k = 0; k < 4; k++) {
    let blade = rect(cx - 7, 24, 14, cx - 30, C.woodDark, st(C.woodDark, 2));
    blade += rect(cx + 7, 34, 46, cx - 70, '#f6efe0', { opacity: 0.95, ...st('#e0d4bb', 2) });
    for (let i = 1; i < 5; i++) blade += line(cx + 7, 34 + i * ((cx - 70) / 5), cx + 53, 34 + i * ((cx - 70) / 5), '#cbbd9f', 2);
    d += g(blade, { transform: `rotate(${k * 90} ${cx} ${cx})` });
  }
  d += circle(cx, cx, 20, '#6f4730', st('#6f4730')) + circle(cx, cx, 7, C.gold);
  return S.svg(W, W, d);
}

function coop() {
  const W = 3 * T, H = Math.round(3.5 * T);
  let d = shadow(W / 2, H - 12, 170, 16, 0.25);
  d += rect(62, 360, 14, 76, C.woodDark) + rect(W - 76, 360, 14, 76, C.woodDark);
  d += rect(40, 200, W - 80, 176, '#dca46a', st('#dca46a', 3.5));
  d += planks(40, 200, W - 80, 176, '#dca46a', 7, true);
  d += path('M150,376 V310 Q150,282 184,282 Q218,282 218,310 V376 Z', '#7a4b2a', st('#7a4b2a'));
  d += path(`M150,376 L96,${H - 8} L128,${H - 8} L200,376 Z`, C.woodLight, st(C.woodLight, 2));
  for (let i = 1; i < 4; i++) d += line(150 - 14 * i, 376 + 18 * i, 200 - 18 * i, 376 + 18 * i, C.woodDark, 2);
  d += circle(288, 262, 24, C.glass, st(C.white, 5));
  d += polygon([[20, 214], [W - 20, 214], [W - 92, 70], [92, 70]], '#cf5a48', st('#cf5a48', 4));
  for (let i = 1; i < 4; i++) d += line(92 - 18 * i, 70 + 36 * i, W - 92 + 18 * i, 70 + 36 * i, shade('#cf5a48', -0.2), 3);
  d += rect(86, 62, W - 172, 18, shade('#cf5a48', -0.25), { rx: 8 });
  // Egg sign
  d += ellipse(96, 270, 16, 20, '#fff8ea', st('#fff8ea', 2));
  return S.svg(W, H, d);
}

function barn() {
  const W = 4 * T, H = Math.round(4.8 * T);
  let d = shadow(W / 2, H - 12, 240, 20, 0.25);
  d += rect(40, 240, W - 80, H - 250, C.red, st(C.red, 4));
  d += planks(40, 240, W - 80, H - 250, C.red, 12, true);
  d += rect(40, 240, W - 80, 10, C.white) + rect(40, H - 20, W - 80, 10, C.white);
  // Big doors with X braces.
  d += rect(166, 400, 180, H - 410, shade(C.red, -0.15), st(C.red, 4));
  d += rect(166, 400, 180, H - 410, 'none', { stroke: C.white, strokeWidth: 10 });
  d += line(256, 400, 256, H - 10, C.white, 6);
  for (const [x0, x1] of [[166, 256], [256, 346]]) {
    d += line(x0, 400, x1, H - 10, C.white, 7) + line(x1, 400, x0, H - 10, C.white, 7);
  }
  d += rect(74, 320, 60, 50, C.glass, { rx: 4, ...st(C.white, 5) }) + rect(W - 134, 320, 60, 50, C.glass, { rx: 4, ...st(C.white, 5) });
  // Gambrel roof and hay loft.
  d += polygon([[18, 256], [70, 150], [176, 66], [W - 176, 66], [W - 70, 150], [W - 18, 256]], '#8d3d2f', st('#8d3d2f', 4));
  d += polyline([[70, 150], [W - 70, 150]], shade('#8d3d2f', -0.2), 4);
  for (let i = 1; i < 4; i++) d += line(40, 150 + i * 26, W - 40, 150 + i * 26, shade('#8d3d2f', -0.18), 2.5);
  d += rect(214, 150, 84, 76, '#fff3d6', st(C.white, 3)) + rect(222, 158, 68, 60, '#7a3a2c');
  d += line(222, 158, 290, 218, C.white, 5) + line(290, 158, 222, 218, C.white, 5);
  d += path('M226,214 q14,-14 30,-4 q14,-12 30,2 v4 h-60 z', C.gold);
  return S.svg(W, H, d);
}

function polyline(pts, stroke, w) {
  return path('M' + pts.map((p) => p.join(',')).join(' L'), 'none', { stroke, strokeWidth: w, strokeLinecap: 'round' });
}

function trough(kind, level) {
  const W = T, H = Math.round(1.4 * T);
  let d = shadow(64, H - 10, 56, 12);
  if (kind === 'hopper') {
    d += rect(18, 60, 92, 100, C.wood, st(C.wood));
    d += planks(18, 60, 92, 100, C.wood, 3);
    d += path('M10,62 L64,30 L118,62 Z', C.roof, st(C.roof));
    d += rect(14, 140, 100, 28, C.woodDark, { rx: 6, ...st(C.woodDark) });
    if (level > 0) d += ellipse(64, 146, level === 2 ? 44 : 26, level === 2 ? 9 : 6, C.gold);
  } else if (kind === 'hay_rack') {
    d += rect(14, 52, 100, 112, 'none', { stroke: C.woodDark, strokeWidth: 8, rx: 4 });
    for (let i = 1; i < 6; i++) d += line(14 + i * 17, 52, 14 + i * 17, 164, C.woodDark, 4);
    if (level > 0) {
      const top = level === 2 ? 60 : 110;
      const r = S.rng(level + 3);
      d += path(`M18,160 V${top + 10} Q64,${top - 14} 110,${top + 10} V160 Z`, '#e8c766', { opacity: 0.95 });
      for (let i = 0; i < 14; i++) {
        const x = 22 + r() * 84, y = top + 6 + r() * (150 - top);
        d += line(x, y, x + (r() - 0.5) * 22, y - 10, '#c9a43f', 2);
      }
      for (let i = 1; i < 6; i++) d += line(14 + i * 17, 52, 14 + i * 17, 164, C.woodDark, 4);
    }
    d += rect(8, 156, 112, 16, C.woodDark, { rx: 4 });
  } else {
    d += path('M8,128 L18,170 H110 L120,128 Z', C.wood, st(C.wood));
    d += rect(8, 120, 112, 14, C.woodDark, { rx: 6 });
    if (level > 0) {
      const r = S.rng(level + 9);
      const n = level === 2 ? 11 : 5;
      for (let i = 0; i < n; i++) {
        const x = 20 + r() * 88, y = 112 + r() * 14;
        for (let k = 0; k < 3; k++) d += circle(x + Math.cos(k * 2.1) * 6, y + Math.sin(k * 2.1) * 6, 6, k % 2 ? C.leaf : C.leafLight);
      }
      if (level === 2) d += circle(64, 108, 6, '#f2a6c4');
    }
  }
  return S.svg(W, H, d);
}

function cheesePress() {
  const W = T, H = Math.round(1.6 * T);
  let d = shadow(64, H - 10, 54, 12);
  d += rect(16, 60, 14, 130, C.woodDark, st(C.woodDark, 2)) + rect(98, 60, 14, 130, C.woodDark, st(C.woodDark, 2));
  d += rect(8, 56, 112, 18, C.wood, { rx: 4, ...st(C.wood) });
  d += rect(58, 28, 12, 100, '#8f8f8f', st('#8f8f8f', 2));
  for (let i = 0; i < 6; i++) d += line(58, 80 + i * 8, 70, 76 + i * 8, '#6d6d6d', 2);
  d += rect(36, 22, 56, 10, C.woodDark, { rx: 5 });
  d += rect(30, 132, 68, 34, '#f2c94c', { rx: 10, ...st('#f2c94c') }) + ellipse(64, 134, 34, 9, '#f8dc7a');
  d += rect(10, 166, 108, 22, C.wood, { rx: 6, ...st(C.wood) });
  return S.svg(W, H, d);
}

function loom() {
  const W = T, H = Math.round(1.6 * T);
  let d = shadow(64, H - 10, 56, 12);
  d += rect(12, 36, 12, 156, C.woodDark, st(C.woodDark, 2)) + rect(104, 36, 12, 156, C.woodDark, st(C.woodDark, 2));
  d += rect(6, 34, 116, 14, C.wood, { rx: 4, ...st(C.wood) });
  for (let i = 0; i < 9; i++) d += line(28 + i * 9, 48, 28 + i * 9, 150, '#efe6d6', 2);
  ['#d9644a', '#f2c94c', '#5f9fd6', '#d9644a'].forEach((c, i) => { d += rect(26, 98 + i * 12, 76, 12, c); });
  d += rect(10, 146, 108, 14, C.wood, { rx: 4, ...st(C.wood) });
  d += rect(20, 176, 88, 14, C.woodDark, { rx: 4 });
  return S.svg(W, H, d);
}

function pad() {
  const W = T, H = Math.round(1.25 * T);
  let d = rect(10, 92, 108, 60, C.stoneLight, { rx: 12, ...st(C.stoneLight), opacity: 0.85 });
  d += rect(58, 40, 10, 76, C.woodDark);
  d += rect(30, 26, 66, 40, C.wood, { rx: 6, ...st(C.wood) });
  d += circle(63, 46, 13, C.gold, st(C.gold, 2));
  d += path('M58,46 h10 M63,41 v10', 'none', { stroke: ink(C.gold), strokeWidth: 3, strokeLinecap: 'round' });
  return S.svg(W, H, d);
}

function sign() {
  const W = T, H = Math.round(1.6 * T);
  let d = shadow(64, H - 10, 30, 8);
  d += rect(57, 70, 14, H - 80, C.woodDark, st(C.woodDark, 2));
  d += path('M8,36 H96 L120,64 L96,92 H8 Z', C.wood, st(C.wood, 3));
  d += path('M26,64 H82 M66,48 L84,64 L66,80', 'none', { stroke: '#fff7e6', strokeWidth: 9, strokeLinecap: 'round', strokeLinejoin: 'round' });
  return S.svg(W, H, d);
}

const STALLS = {
  seeds: { a: '#5aa95b', b: '#f6f1e3', goods: ['#e86a5a', '#f2c94c', '#8bc34a', '#ffffff', '#ff9e57'] },
  feed: { a: '#c98e3a', b: '#f6eccc', goods: ['#e5c060', '#e8cf7a', '#6fae4f', '#d9a066'] },
  produce: { a: '#ef8a3c', b: '#fff4e2', goods: ['#e2493b', '#ff9a35', '#f6d04d', '#7cc45f', '#c0392b'] },
};

function stallBack(kind) {
  const W = 3 * T, H = Math.round(3.6 * T);
  const s = STALLS[kind];
  let d = shadow(W / 2, H - 12, 180, 16, 0.22);
  d += rect(34, 120, 16, H - 140, C.woodDark, st(C.woodDark, 2)) + rect(W - 50, 120, 16, H - 140, C.woodDark, st(C.woodDark, 2));
  d += rect(50, 170, W - 100, 150, shade(C.wood, -0.1), st(C.wood));
  d += rect(50, 230, W - 100, 10, C.woodDark) + rect(50, 290, W - 100, 10, C.woodDark);
  const r = S.rng(kind.length * 11);
  for (let row = 0; row < 2; row++) {
    for (let i = 0; i < 8; i++) {
      const x = 70 + i * 32, y = 222 + row * 60;
      d += circle(x, y - 10, 11, s.goods[Math.floor(r() * s.goods.length)], { stroke: '#00000022', strokeWidth: 2 });
    }
  }
  // Striped awning with a scalloped edge.
  const top = 64, bot = 168, n = 8, sw = (W - 24) / n;
  for (let i = 0; i < n; i++) {
    d += path(`M${12 + i * sw},${bot} L${30 + i * sw * 0.9},${top} H${30 + (i + 1) * sw * 0.9} L${12 + (i + 1) * sw},${bot} Z`, i % 2 ? s.b : s.a);
  }
  for (let i = 0; i < n; i++) {
    d += path(`M${12 + i * sw},${bot} q${sw / 2},${28} ${sw},0 Z`, i % 2 ? s.b : s.a, { stroke: ink(s.a), strokeWidth: 2 });
  }
  d += path(`M12,${bot} L30,${top} H${W - 30} L${W - 12},${bot}`, 'none', st(s.a, 3));
  return S.svg(W, H, d);
}

function stallFront(kind) {
  const W = 3 * T, H = Math.round(3.6 * T);
  const s = STALLS[kind];
  let d = rect(22, 318, W - 44, H - 330, C.wood, st(C.wood, 3));
  d += planks(22, 318, W - 44, H - 330, C.wood, 6, true);
  d += rect(14, 304, W - 28, 24, C.woodLight, { rx: 6, ...st(C.woodLight) });
  const r = S.rng(kind.length * 5);
  if (kind === 'seeds') {
    for (let i = 0; i < 7; i++) {
      const x = 44 + i * 44;
      d += rect(x - 15, 268, 30, 40, '#fff8e6', { rx: 4, ...st('#fff8e6', 2) }) + circle(x, 284, 9, s.goods[i % 5]);
    }
  } else if (kind === 'feed') {
    for (let i = 0; i < 4; i++) {
      const x = 70 + i * 82, c = ['#e5c060', '#e8cf7a', '#86b85b', '#e5c060'][i];
      d += path(`M${x - 30},306 Q${x - 34},262 ${x - 18},252 H${x + 18} Q${x + 34},262 ${x + 30},306 Z`, '#d9c49a', st('#d9c49a', 2));
      d += ellipse(x, 254, 18, 7, c);
    }
  } else {
    for (let i = 0; i < 4; i++) {
      const x = 66 + i * 84;
      d += path(`M${x - 34},304 L${x - 28},270 H${x + 28} L${x + 34},304 Z`, C.woodDark, st(C.woodDark, 2));
      for (let k = 0; k < 5; k++) d += circle(x - 20 + k * 10, 266 - (k % 2) * 6, 10, s.goods[Math.floor(r() * s.goods.length)]);
    }
  }
  return S.svg(W, H, d);
}

function workshop() {
  const W = 5 * T, H = Math.round(4.6 * T);
  let d = shadow(W / 2, H - 12, 300, 20, 0.25);
  d += rect(40, 250, W - 80, H - 260, C.wood, st(C.wood, 4));
  d += planks(40, 250, W - 80, H - 260, C.wood, 9);
  // Counter opening where Tural stands.
  d += rect(220, 330, 200, 140, '#4a3426', { rx: 8 });
  d += rect(232, 342, 176, 116, '#5c4130', { rx: 6 });
  for (let i = 0; i < 4; i++) d += rect(244 + i * 42, 352, 8, 30, '#8d8d8d', { rx: 3 });
  d += path('M200,330 L216,296 H424 L440,330 Z', C.slate, st(C.slate, 3));
  // Side door and window.
  d += rect(72, 420, 90, H - 432, C.woodDark, st(C.woodDark)) + circle(148, 500, 5, C.gold);
  d += windowBox(W - 180, 360, 100, 80, false);
  // Roof and a hammer-and-saw sign.
  d += polygon([[14, 266], [W - 14, 266], [W - 90, 84], [90, 84]], C.slate, st(C.slate, 4));
  for (let i = 1; i < 5; i++) d += line(90 - 15 * i, 84 + 36 * i, W - 90 + 15 * i, 84 + 36 * i, shade(C.slate, -0.2), 3);
  d += rect(84, 74, W - 168, 20, shade(C.slate, -0.3), { rx: 8 });
  d += rect(250, 150, 140, 70, C.woodLight, { rx: 10, ...st(C.woodLight) });
  d += path('M285,200 L320,165 M312,160 h24 v14 h-24 z', 'none', { stroke: '#5b5b5b', strokeWidth: 8, strokeLinecap: 'round' });
  d += path('M330,200 l30,-34 l8,6 l-30,34 z', '#9aa3ab', st('#9aa3ab', 2));
  // Logs by the wall.
  for (let i = 0; i < 3; i++) d += circle(W - 66 - i * 30, H - 40, 16, C.woodLight, st(C.woodDark, 3)) + circle(W - 66 - i * 30, H - 40, 6, C.woodDark);
  return S.svg(W, H, d);
}

function workshopFront() {
  const W = 5 * T, H = Math.round(4.6 * T);
  return S.svg(W, H, rect(212, 452, 216, 26, C.woodLight, { rx: 6, ...st(C.woodLight) }));
}

function board() {
  const W = 2 * T, H = Math.round(2.3 * T);
  let d = shadow(W / 2, H - 10, 110, 12);
  d += rect(30, 150, 14, H - 160, C.woodDark) + rect(W - 44, 150, 14, H - 160, C.woodDark);
  d += rect(16, 68, W - 32, 160, '#c79a63', st('#c79a63', 4));
  d += rect(26, 78, W - 52, 140, '#b98a55');
  const notes = [[40, 88, '#fff7e0'], [110, 96, '#fff2b3'], [170, 86, '#e8f4ff'], [64, 150, '#ffe6e6'], [140, 152, '#fff7e0']];
  for (const [x, y, c] of notes) {
    d += rect(x, y, 52, 56, c, { rx: 3, ...st(c, 2), transform: `rotate(${(x % 7) - 3} ${x + 26} ${y + 28})` });
    d += line(x + 8, y + 22, x + 44, y + 22, '#b9b0a0', 2.5) + line(x + 8, y + 34, x + 38, y + 34, '#b9b0a0', 2.5);
    d += circle(x + 26, y + 6, 5, '#e2493b');
  }
  d += path(`M6,72 L${W / 2},34 L${W - 6},72 Z`, C.roof, st(C.roof, 3));
  return S.svg(W, H, d);
}

function fountain() {
  const W = 4 * T, H = Math.round(3.9 * T);
  let d = shadow(W / 2, H - 20, 240, 26, 0.2);
  d += path(`M24,300 V${H - 70} Q256,${H + 20} 488,${H - 70} V300 Z`, C.stone, st(C.stone, 4));
  for (let i = 0; i < 9; i++) d += line(52 + i * 52, 318, 52 + i * 52, H - 50 - Math.abs(i - 4) * 4, shade(C.stone, -0.18), 3);
  d += ellipse(256, 300, 232, 96, C.stoneLight, st(C.stoneLight, 4));
  d += ellipse(256, 304, 202, 76, '#62b6e0');
  d += ellipse(220, 290, 90, 24, '#93d3f1', { opacity: 0.6 });
  d += rect(238, 150, 36, 150, C.stoneLight, st(C.stoneLight, 3));
  d += ellipse(256, 150, 62, 22, C.stoneLight, st(C.stoneLight, 3)) + ellipse(256, 150, 48, 14, '#62b6e0');
  for (const s of [-1, 1]) {
    d += path(`M256,120 Q${256 + s * 70},90 ${256 + s * 120},290`, 'none', { stroke: '#cfeffd', strokeWidth: 6, opacity: 0.85, strokeLinecap: 'round' });
  }
  d += path('M256,148 Q256,80 256,60', 'none', { stroke: '#e6f7ff', strokeWidth: 8, strokeLinecap: 'round' });
  return S.svg(W, H, d);
}

function lamp() {
  const W = T, H = Math.round(2.6 * T);
  let d = shadow(64, H - 10, 26, 8);
  d += rect(58, 96, 12, H - 112, '#3d4a3f') + rect(46, H - 30, 36, 20, '#3d4a3f', { rx: 6 });
  d += path('M64,96 Q64,70 86,70', 'none', { stroke: '#3d4a3f', strokeWidth: 6 });
  d += rect(42, 34, 44, 54, '#ffe39a', { rx: 8, ...st('#3d4a3f', 5) });
  d += path('M36,36 L64,14 L92,36 Z', '#3d4a3f');
  return S.svg(W, H, d);
}

function bench() {
  const W = 2 * T, H = Math.round(1.25 * T);
  let d = shadow(W / 2, H - 10, 116, 12);
  d += rect(28, 100, 12, 54, C.woodDark) + rect(W - 40, 100, 12, 54, C.woodDark);
  d += rect(12, 22, W - 24, 18, C.wood, { rx: 6, ...st(C.wood, 2) }) + rect(12, 50, W - 24, 18, C.wood, { rx: 6, ...st(C.wood, 2) });
  d += rect(8, 88, W - 16, 22, C.woodLight, { rx: 6, ...st(C.woodLight, 2) });
  return S.svg(W, H, d);
}

// ------------------------------------------------------------------ interior

function bed() {
  const W = 2 * T, H = Math.round(2.3 * T);
  let d = shadow(W / 2, H - 8, 120, 10, 0.18);
  d += rect(16, 18, W - 32, 76, C.woodDark, { rx: 18, ...st(C.woodDark) });
  d += rect(24, 70, W - 48, H - 98, C.white, { rx: 12, ...st(C.white, 2) });
  d += rect(48, 86, W - 96, 46, '#ffffff', { rx: 18, ...st('#e6dfd2', 2) });
  d += path(`M24,150 H${W - 24} V${H - 34} Q${W - 24},${H - 22} ${W - 36},${H - 22} H36 Q24,${H - 22} 24,${H - 34} Z`, '#5f9fd6', st('#5f9fd6', 2));
  for (let i = 0; i < 4; i++) for (let j = 0; j < 2; j++) {
    if ((i + j) % 2) d += rect(30 + i * 50, 156 + j * 50, 46, 46, '#7fb6e3', { rx: 4 });
  }
  d += rect(16, H - 30, W - 32, 22, C.woodDark, { rx: 8 });
  return S.svg(W, H, d);
}

function tv() {
  const W = T, H = Math.round(1.9 * T);
  let d = shadow(64, H - 8, 56, 8, 0.18);
  d += rect(12, 150, 104, 88, C.wood, { rx: 6, ...st(C.wood) }) + rect(20, 166, 40, 60, shade(C.wood, -0.08), { rx: 4 }) + rect(68, 166, 40, 60, shade(C.wood, -0.08), { rx: 4 });
  d += path('M50,58 L34,22 M78,58 L96,24', 'none', { stroke: '#555', strokeWidth: 4, strokeLinecap: 'round' });
  d += rect(16, 56, 96, 96, '#e8d3b0', { rx: 16, ...st('#e8d3b0') });
  d += rect(26, 68, 76, 62, '#2f6b6b', { rx: 12 });
  d += path('M34,76 q12,-4 22,4', 'none', { stroke: '#8fd0c6', strokeWidth: 5, strokeLinecap: 'round', opacity: 0.8 });
  d += circle(98, 140, 4, '#e2493b');
  return S.svg(W, H, d);
}

function stove() {
  const W = 2 * T, H = Math.round(1.9 * T);
  let d = shadow(W / 2, H - 8, 120, 8, 0.18);
  d += rect(8, 120, W - 16, H - 126, '#f1ece2', { rx: 6, ...st('#f1ece2') });
  d += rect(8, 112, W - 16, 18, C.wood, { rx: 4 });
  d += rect(20, 140, 100, 80, '#5b5b5b', { rx: 6 }) + rect(30, 150, 80, 50, '#3b3b3b', { rx: 4 });
  d += rect(138, 140, 100, 80, '#e6dccb', { rx: 6, ...st('#e6dccb', 2) });
  d += circle(44, 116, 14, '#3b3b3b') + circle(96, 116, 14, '#3b3b3b');
  d += path('M24,100 Q24,64 70,64 Q116,64 116,100 Z', '#d9644a', st('#d9644a'));
  d += rect(18, 96, 104, 12, '#b24c37', { rx: 5 });
  d += circle(60, 50, 8, '#ffffff', { opacity: 0.45 }) + circle(76, 36, 10, '#ffffff', { opacity: 0.35 });
  d += rect(148, 88, 80, 26, C.woodLight, { rx: 6, ...st(C.woodLight, 2) });
  d += circle(172, 92, 9, '#ff9a35') + circle(194, 94, 8, '#7cc45f') + circle(212, 92, 7, '#e2493b');
  return S.svg(W, H, d);
}

function table() {
  const W = 2 * T, H = Math.round(1.4 * T);
  let d = shadow(W / 2, H - 8, 110, 10, 0.18);
  d += rect(30, 100, 14, 72, C.woodDark) + rect(W - 44, 100, 14, 72, C.woodDark);
  d += rect(14, 58, W - 28, 60, C.wood, { rx: 14, ...st(C.wood) });
  d += rect(14, 104, W - 28, 14, C.woodDark, { rx: 6 });
  d += path('M118,78 Q114,58 122,48 H134 Q142,58 138,78 Z', '#5f9fd6', st('#5f9fd6', 2));
  d += circle(118, 38, 9, '#f06f8f') + circle(136, 34, 9, '#ffd54f') + circle(128, 24, 9, '#ffffff');
  return S.svg(W, H, d);
}

function plant() {
  const W = T, H = Math.round(1.8 * T);
  let d = shadow(64, H - 8, 34, 8, 0.18);
  d += path(`M36,160 H92 L84,${H - 10} H44 Z`, '#d9784a', st('#d9784a'));
  d += rect(30, 152, 68, 16, '#c0653b', { rx: 5 });
  const r = S.rng(5);
  for (let i = 0; i < 7; i++) {
    const a = -Math.PI / 2 + (i - 3) * 0.42;
    const x = 64 + Math.cos(a) * 52, y = 150 + Math.sin(a) * 92;
    d += path(`M64,154 Q${(64 + x) / 2 + (r() - 0.5) * 20},${(154 + y) / 2} ${x},${y} Q${(64 + x) / 2 + 18},${(154 + y) / 2 + 10} 64,154`, i % 2 ? C.leaf : C.leafDark, st(C.leafDark, 2));
  }
  return S.svg(W, H, d);
}

function shelf() {
  const W = T, H = Math.round(2.2 * T);
  let d = shadow(64, H - 6, 56, 6, 0.18);
  d += rect(12, 18, 104, H - 24, C.woodDark, { rx: 4, ...st(C.woodDark) });
  d += rect(20, 26, 88, H - 40, shade(C.woodDark, -0.25));
  const cols = ['#d9644a', '#5f9fd6', '#f2c94c', '#7cc45f', '#a77bca', '#ef8a3c'];
  for (let s = 0; s < 3; s++) {
    const y = 34 + s * 76;
    let x = 24;
    let i = s * 2;
    while (x < 100) {
      const w = 9 + ((i * 7) % 6);
      d += rect(x, y + 14 + ((i * 3) % 10), w, 52 - ((i * 3) % 10), cols[i % cols.length], { rx: 2 });
      x += w + 2;
      i++;
    }
    d += rect(16, y + 66, 96, 8, C.wood);
  }
  return S.svg(W, H, d);
}

function windowSprite() {
  let d = rect(20, 18, 88, 84, '#fff7ea', { rx: 8, ...st('#fff7ea', 3) });
  d += rect(28, 26, 72, 68, '#bfe6f8', { rx: 4 });
  d += path('M32,90 L58,30', 'none', { stroke: '#ecf9ff', strokeWidth: 8, strokeLinecap: 'round' });
  d += rect(62, 26, 4, 68, '#fff7ea') + rect(28, 58, 72, 4, '#fff7ea');
  d += path('M16,14 Q34,60 22,108 H10 V14 Z', '#e0796b') + path('M112,14 Q94,60 106,108 H118 V14 Z', '#e0796b');
  d += rect(8, 8, 112, 10, C.woodDark, { rx: 4 });
  return S.svg(T, T, d);
}

function rug() {
  // A small Azerbaijani-style carpet: border, zigzag band and a central medallion.
  const W = 6 * T, H = 3 * T;
  let d = rect(16, 16, W - 32, H - 32, '#a63a34', { rx: 18, ...st('#a63a34', 4) });
  d += rect(36, 36, W - 72, H - 72, '#1f4e79', { rx: 10 });
  d += rect(56, 56, W - 112, H - 112, '#b8453c', { rx: 6 });
  let zig = 'M44,44';
  for (let x = 44; x < W - 44; x += 24) zig += ` L${x + 12},54 L${x + 24},44`;
  d += path(zig, 'none', { stroke: '#f2c94c', strokeWidth: 4 });
  let zig2 = `M44,${H - 44}`;
  for (let x = 44; x < W - 44; x += 24) zig2 += ` L${x + 12},${H - 54} L${x + 24},${H - 44}`;
  d += path(zig2, 'none', { stroke: '#f2c94c', strokeWidth: 4 });
  const cx = W / 2, cy = H / 2;
  d += polygon([[cx, cy - 92], [cx + 150, cy], [cx, cy + 92], [cx - 150, cy]], '#1f4e79', st('#1f4e79', 3));
  d += polygon([[cx, cy - 60], [cx + 96, cy], [cx, cy + 60], [cx - 96, cy]], '#f2e2c0');
  d += polygon([[cx, cy - 30], [cx + 46, cy], [cx, cy + 30], [cx - 46, cy]], '#a63a34');
  for (const s of [-1, 1]) {
    d += polygon([[cx + s * 240, cy - 50], [cx + s * 290, cy], [cx + s * 240, cy + 50], [cx + s * 190, cy]], '#f2e2c0', { opacity: 0.9 });
    d += circle(cx + s * 240, cy, 14, '#1f4e79');
  }
  return S.svg(W, H, d);
}

function wallFace() {
  let d = rect(0, 0, T, T, '#f0dcbc');
  for (let x = 8; x < T; x += 32) d += rect(x, 0, 12, T - 18, '#e8cfa8');
  d += rect(0, 0, T, 10, '#d8b88e');
  d += rect(0, T - 18, T, 18, C.woodDark) + rect(0, T - 18, T, 4, C.woodLight);
  return S.svg(T, T, d);
}
const wallTop = () => S.svg(T, T, rect(0, 0, T, T, '#6b4a35') + rect(0, 0, T, 8, '#7d5a43'));
const doormat = () => S.svg(T, T, rect(18, 30, 92, 70, '#8bb35f', { rx: 10, ...st('#8bb35f', 3) }) + rect(30, 42, 68, 46, '#a5c97a', { rx: 6 }));

// ------------------------------------------------------------------ props

function tree(variant) {
  const W = 2 * T, H = Math.round(2.6 * T);
  const r = S.rng(100 + variant * 17);
  let d = shadow(W / 2, H - 14, 76, 18, 0.25);
  d += path(`M112,${H - 16} L118,200 H138 L144,${H - 16} Q128,${H - 6} 112,${H - 16} Z`, '#8a5a3c', st('#8a5a3c', 2.5));
  const dark = variant === 2 ? '#4c8f43' : C.leafDark;
  d += path(blob(128, 150, 104, 12, 0.22, r), dark, st(dark, 3));
  d += path(blob(116, 132, 82, 11, 0.25, r), C.leaf);
  d += path(blob(98, 108, 48, 9, 0.3, r), C.leafLight, { opacity: 0.9 });
  if (variant === 2) for (let i = 0; i < 7; i++) d += circle(70 + r() * 116, 100 + r() * 100, 8, '#e2493b', st('#e2493b', 2));
  if (variant === 1) for (let i = 0; i < 6; i++) d += circle(64 + r() * 128, 96 + r() * 110, 6, '#fff3f6');
  return S.svg(W, H, d);
}

function pine() {
  const W = Math.round(1.5 * T), H = Math.round(2.6 * T);
  let d = shadow(W / 2, H - 14, 60, 14, 0.25);
  d += rect(W / 2 - 10, H - 70, 20, 56, '#7a4e33');
  const layers = [[60, 150, 86], [110, 210, 92], [160, 270, 96]];
  for (const [top, bot, hw] of layers) {
    d += path(`M${W / 2},${top} L${W / 2 + hw},${bot} Q${W / 2},${bot + 18} ${W / 2 - hw},${bot} Z`, '#3f8248', st('#3f8248', 3));
    d += path(`M${W / 2},${top + 12} L${W / 2 - hw * 0.6},${bot - 8} Q${W / 2 - 20},${bot} ${W / 2},${bot - 4} Z`, '#56a15a', { opacity: 0.9 });
  }
  return S.svg(W, H, d);
}

function bush(variant) {
  const W = Math.round(1.25 * T), H = Math.round(1.1 * T);
  const r = S.rng(300 + variant);
  let d = shadow(W / 2, H - 12, 64, 12, 0.22);
  d += path(blob(W / 2, 84, 60, 10, 0.25, r, 0.75), C.leafDark, st(C.leafDark, 3));
  d += path(blob(W / 2 - 10, 74, 42, 9, 0.3, r, 0.75), C.leaf);
  if (variant === 1) for (let i = 0; i < 6; i++) d += circle(26 + r() * 108, 54 + r() * 50, 6, '#f06f8f');
  return S.svg(W, H, d);
}

function rock(variant) {
  const W = T, H = Math.round(0.85 * T);
  const r = S.rng(400 + variant);
  let d = shadow(64, H - 12, 52, 12, 0.22);
  d += path(blob(64, 66, 46, 9, 0.25, r, 0.7), '#a7a39b', st('#a7a39b', 3));
  d += path(blob(54, 56, 24, 7, 0.3, r, 0.6), '#c7c3bb');
  return S.svg(W, H, d);
}

function flowers(variant) {
  const r = S.rng(500 + variant * 3);
  const cols = [['#ffffff', '#ffd54f'], ['#f06f8f', '#fff3b0'], ['#a77bca', '#ffe082'], ['#ffd54f', '#e2493b']];
  let d = '';
  for (let i = 0; i < 7; i++) {
    const x = 18 + r() * 92, y = 30 + r() * 80;
    const [p, c] = cols[Math.floor(r() * cols.length)];
    d += line(x, y + 4, x + (r() - 0.5) * 6, y + 18, '#5c9c46', 3);
    for (let k = 0; k < 5; k++) d += circle(x + Math.cos(k * 1.256) * 6, y + Math.sin(k * 1.256) * 6, 5, p);
    d += circle(x, y, 3.5, c);
  }
  return S.svg(T, T, d);
}

function tuft(variant) {
  const r = S.rng(600 + variant);
  let d = '';
  for (let i = 0; i < 3; i++) {
    const x = 24 + r() * 80, y = 60 + r() * 50;
    for (let k = -2; k <= 2; k++) d += path(`M${x},${y} q${k * 4},-10 ${k * 8},-${18 + Math.abs(k) * -3}`, 'none', { stroke: k % 2 ? '#5f9e45' : '#73b453', strokeWidth: 3.5, strokeLinecap: 'round' });
  }
  return S.svg(T, T, d);
}

function fencePost() {
  const H = Math.round(1.35 * T);
  let d = ellipse(64, H - 12, 18, 6, '#1d2a12', { opacity: 0.2 });
  d += rect(55, 54, 18, H - 66, C.wood, st(C.wood, 2.5)) + path('M55,56 L64,44 L73,56 Z', C.woodLight, st(C.wood, 2));
  return S.svg(T, H, d);
}
function fenceH() {
  const H = Math.round(1.35 * T);
  return S.svg(T, H, rect(0, 78, T, 12, C.woodLight, st(C.wood, 2)) + rect(0, 112, T, 12, C.woodLight, st(C.wood, 2)));
}
function fenceV() {
  const H = Math.round(1.35 * T);
  return S.svg(T, H, rect(58, 0, 12, T, C.woodLight, st(C.wood, 2)));
}
function gate() {
  const H = Math.round(1.35 * T);
  let d = rect(4, 54, 16, H - 66, C.wood, st(C.wood, 2.5)) + rect(108, 54, 16, H - 66, C.wood, st(C.wood, 2.5));
  d += path('M20,84 L52,70 L52,128 L20,134 Z', C.woodLight, st(C.wood, 2));
  d += path('M108,84 L76,70 L76,128 L108,134 Z', C.woodLight, st(C.wood, 2));
  return S.svg(T, H, d);
}

function stake() {
  const H = Math.round(0.9 * T);
  return S.svg(T, H, rect(58, 40, 12, 64, C.woodDark, st(C.woodDark, 2)) + path('M58,104 L64,112 L70,104 Z', C.woodDark) + rect(52, 52, 24, 8, '#e2493b', { rx: 3 }));
}

function lockSign() {
  const H = Math.round(1.4 * T);
  let d = shadow(64, H - 10, 30, 8);
  d += rect(57, 90, 14, H - 100, C.woodDark);
  d += rect(14, 30, 100, 70, C.wood, { rx: 10, ...st(C.wood, 3) });
  d += path('M50,62 V52 Q50,40 64,40 Q78,40 78,52 V62', 'none', { stroke: '#6d6d6d', strokeWidth: 7 });
  d += rect(44, 60, 40, 30, '#f2c94c', { rx: 6, ...st('#f2c94c', 2) }) + circle(64, 74, 4, ink('#f2c94c'));
  return S.svg(T, H, d);
}

// ------------------------------------------------------------------ field plots

function tilled(wet) {
  const base = wet ? '#6f4b2d' : '#9c6b3f';
  const furrow = wet ? '#553822' : '#7e5430';
  const ridge = wet ? '#82593a' : '#b17d4b';
  let d = path(rrect(7, 7, 114, 114, 22), base, { stroke: furrow, strokeWidth: 3 });
  for (let i = 0; i < 4; i++) {
    const y = 24 + i * 25;
    d += path(`M18,${y} Q64,${y - 5} 110,${y}`, 'none', { stroke: ridge, strokeWidth: 5, strokeLinecap: 'round' });
    d += path(`M18,${y + 8} Q64,${y + 3} 110,${y + 8}`, 'none', { stroke: furrow, strokeWidth: 4, strokeLinecap: 'round' });
  }
  if (wet) d += path('M26,30 q12,-6 24,0', 'none', { stroke: '#9ecae1', strokeWidth: 3, opacity: 0.5, strokeLinecap: 'round' });
  return S.svg(T, T, d);
}

function fert() {
  const r = S.rng(77);
  let d = '';
  for (let i = 0; i < 18; i++) d += circle(16 + r() * 96, 16 + r() * 96, 3, r() > 0.5 ? '#f2f2f2' : '#a8d8ff', { opacity: 0.9 });
  return S.svg(T, T, d);
}

// ------------------------------------------------------------------ effects

const fx = {
  shadow: () => S.svg(T, 64, S.radial('sh', [[0, '#000', 0.35], [0.7, '#000', 0.18], [1, '#000', 0]]) && ellipse(64, 32, 62, 30, 'url(#sh)'),
    S.radial('sh', [[0, '#000000', 0.38], [0.65, '#000000', 0.2], [1, '#000000', 0]])),
  light: () => S.svg(256, 256, circle(128, 128, 128, 'url(#lg)'), S.radial('lg', [[0, '#fff1c4', 0.95], [0.35, '#ffd98a', 0.5], [1, '#ffb347', 0]])),
  ripple: () => S.svg(T, T, circle(64, 64, 56, 'none', { stroke: '#ffffff', strokeWidth: 7 })),
  marker: () => S.svg(T, T, ellipse(64, 64, 52, 30, 'none', { stroke: '#ffffff', strokeWidth: 8, opacity: 0.9 }) + ellipse(64, 64, 26, 15, '#ffffff', { opacity: 0.55 })),
  rain: () => S.svg(12, 64, rect(3, 0, 6, 64, 'url(#rn)', { rx: 3 }), S.linear('rn', [[0, '#dff3ff', 0], [1, '#dff3ff', 0.9]])),
  sparkle: () => S.svg(48, 48, path('M24,0 Q27,21 48,24 Q27,27 24,48 Q21,27 0,24 Q21,21 24,0 Z', '#fffbe6')),
  smoke: () => S.svg(64, 64, circle(32, 32, 30, 'url(#sm)'), S.radial('sm', [[0, '#ffffff', 0.85], [1, '#ffffff', 0]])),
  dot: () => S.svg(32, 32, circle(16, 16, 16, 'url(#dt)'), S.radial('dt', [[0, '#ffffff', 1], [0.4, '#ffffff', 0.8], [1, '#ffffff', 0]])),
  butterfly_0: () => S.svg(48, 40, ellipse(14, 16, 12, 10, '#ffffff') + ellipse(34, 16, 12, 10, '#ffffff') + ellipse(16, 28, 8, 7, '#f2f2f2') + ellipse(32, 28, 8, 7, '#f2f2f2') + rect(22, 8, 4, 26, '#4a4a4a', { rx: 2 })),
  butterfly_1: () => S.svg(48, 40, ellipse(18, 18, 5, 11, '#ffffff') + ellipse(30, 18, 5, 11, '#ffffff') + rect(22, 8, 4, 26, '#4a4a4a', { rx: 2 })),
  drop: () => S.svg(24, 32, path('M12,0 Q24,18 12,32 Q0,18 12,0 Z', '#bfe8ff')),
  clod: () => S.svg(24, 24, circle(12, 12, 10, '#8a5a34')),
  leaf: () => S.svg(32, 24, path('M2,12 Q16,-4 30,12 Q16,28 2,12 Z', '#7cc45f')),
  // A shooting star flying right: a bright head with a tapering tail (the game rotates it).
  shooting_star: () => S.svg(256, 48,
    path('M0,24 L222,16 Q236,24 222,32 Z', 'url(#ss)') + circle(228, 24, 18, 'url(#sg)') +
    path('M228,10 Q230,22 242,24 Q230,26 228,38 Q226,26 214,24 Q226,22 228,10 Z', '#ffffff'),
    S.linear('ss', [[0, '#fff3c4', 0], [0.75, '#fff3c4', 0.5], [1, '#ffffff', 0.95]], 0, 0, 1, 0) +
    S.radial('sg', [[0, '#ffffff', 1], [0.45, '#fff1b0', 0.75], [1, '#ffe08a', 0]])),
  // The rainbow over the farm the morning after rain: six soft bands, outer radius 250 and inner 178
  // around the middle of the bottom edge (ui/Sky.kt taps against these), fading out towards the ground.
  rainbow: () => S.svg(512, 256,
    g(['#f2837a', '#f7b267', '#f6dd73', '#9fd67f', '#79b8ea', '#ab8fdc']
      .map((c, i) => path(`M${256 - (244 - i * 12)},256 A${244 - i * 12},${244 - i * 12} 0 0 1 ${256 + (244 - i * 12)},256`, 'none', { stroke: c, strokeWidth: 12.5 }))
      .join(''), { mask: 'url(#rbm)' }),
    S.linear('rbf', [[0, '#ffffff', 1], [0.55, '#ffffff', 1], [1, '#ffffff', 0]]) +
    '<mask id="rbm" maskUnits="userSpaceOnUse" x="0" y="0" width="512" height="256"><rect width="512" height="256" fill="url(#rbf)"/></mask>'),
};

module.exports = function objects() {
  const out = [];
  const add = (name, svg) => out.push({ name: 'obj/' + name, svg });
  add('house', house());
  add('well', well());
  add('mill', mill());
  add('mill_sails', millSails());
  add('coop', coop());
  add('barn', barn());
  for (const k of ['hopper', 'hay_rack', 'clover_tray']) for (let l = 0; l < 3; l++) add(`${k}_${l}`, trough(k, l));
  add('cheese_press', cheesePress());
  add('loom', loom());
  add('pad', pad());
  add('sign', sign());
  for (const k of Object.keys(STALLS)) {
    add(`stall_${k}`, stallBack(k));
    add(`stall_${k}_front`, stallFront(k));
  }
  add('workshop', workshop());
  add('workshop_front', workshopFront());
  add('board', board());
  add('fountain', fountain());
  add('lamp', lamp());
  add('bench', bench());
  add('bed', bed());
  add('tv', tv());
  add('stove', stove());
  add('table', table());
  add('plant', plant());
  add('shelf', shelf());
  add('window', windowSprite());
  add('rug', rug());
  add('wall_face', wallFace());
  add('wall_top', wallTop());
  add('doormat', doormat());
  for (let v = 0; v < 3; v++) add(`tree_${v}`, tree(v));
  add('pine', pine());
  for (let v = 0; v < 2; v++) add(`bush_${v}`, bush(v));
  for (let v = 0; v < 2; v++) add(`rock_${v}`, rock(v));
  for (let v = 0; v < 3; v++) add(`flowers_${v}`, flowers(v));
  for (let v = 0; v < 2; v++) add(`tuft_${v}`, tuft(v));
  add('fence_post', fencePost());
  add('fence_h', fenceH());
  add('fence_v', fenceV());
  add('gate', gate());
  add('stake', stake());
  add('lock_sign', lockSign());
  add('tilled', tilled(false));
  add('tilled_wet', tilled(true));
  add('fert', fert());
  for (const [k, f] of Object.entries(fx)) out.push({ name: 'fx/' + k, svg: f() });
  return out;
};
