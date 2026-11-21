// Ground tiles. Grass is the base; every other terrain is drawn with the "dual grid"
// trick: a tile sits on the corner where four map cells meet, and each of its four
// quarters belongs to one cell. 16 corner combinations per terrain give smooth, rounded
// borders. The rounding comes from a blur + threshold ("goo") filter on the quarters.
'use strict';
const S = require('./svg');
const { TILE: T } = S;

// Bits: 1 = top-left, 2 = top-right, 4 = bottom-right, 8 = bottom-left (screen space, y down).
// The border between "on" and "off" quarters only runs along the tile's middle lines, so the
// only corner is at the tile center: round it (convex) or fillet it (concave), and neighbors
// always meet at the edge midpoints.
const H = T / 2;
const R = 56; // corner radius in px (must stay below T/2)

const rot90 = (m) => ((m << 1) | (m >> 3)) & 15;

/** Canonical shapes with the border pushed `e` px into the "on" side. */
function single(e) { // top-left quarter only
  const c = H - e, rc = Math.max(R - e, 2);
  return `M-2,-2H${c}V${c - rc}A${rc},${rc} 0 0 1 ${c - rc},${c}H-2Z`;
}
function half(e) { return `M-2,-2H${T + 2}V${H - e}H-2Z`; } // top half
function three(e) { // all but bottom-right
  const c = H - e, rf = R + e;
  return `M-2,-2H${T + 2}V${c}H${c + rf}A${rf},${rf} 0 0 0 ${c},${c + rf}V${T + 2}H-2Z`;
}
const full = () => `M-2,-2H${T + 2}V${T + 2}H-2Z`;

/** Paths (with rotation) that draw the "on" area of a mask. */
function shapePaths(mask, e) {
  if (mask === 15) return [S.path(full(), '#fff')];
  const out = [];
  const place = (d, canon) => {
    for (let k = 0; k < 4; k++) {
      let m = canon;
      for (let i = 0; i < k; i++) m = rot90(m);
      if (m === mask) return S.path(d, '#fff', { transform: `rotate(${k * 90} ${H} ${H})` });
    }
    return null;
  };
  const bits = [1, 2, 4, 8].filter((b) => mask & b).length;
  if (bits === 1) out.push(place(single(e), 1));
  else if (bits === 3) out.push(place(three(e), 11));
  else if (mask === 5 || mask === 10) {
    // two diagonal quarters: draw each as its own rounded single
    for (const b of [1, 2, 4, 8]) if (mask & b) {
      for (let k = 0; k < 4; k++) {
        let m = 1;
        for (let i = 0; i < k; i++) m = rot90(m);
        if (m === b) out.push(S.path(single(e), '#fff', { transform: `rotate(${k * 90} ${H} ${H})` }));
      }
    }
  } else out.push(place(half(e), 3));
  return out.filter(Boolean);
}

function clipDefs(id, mask, rim) {
  return S.el('clipPath', { id: id + 'o' }, shapePaths(mask, 0).join('')) +
    S.el('clipPath', { id: id + 'n' }, shapePaths(mask, rim).join('')) +
    S.el('clipPath', { id: id + 'd' }, shapePaths(mask, rim + 12).join(''));
}

/** Small details inside a full tile, kept away from the edges so tiles never cut them. */
function speckles(seed, n, colors, rMin, rMax, margin = 10) {
  const r = S.rng(seed);
  let out = '';
  for (let i = 0; i < n; i++) {
    const x = margin + r() * (T - margin * 2), y = margin + r() * (T - margin * 2);
    const rr = rMin + r() * (rMax - rMin);
    out += S.ellipse(x.toFixed(1), y.toFixed(1), rr.toFixed(1), (rr * 0.7).toFixed(1), colors[Math.floor(r() * colors.length)]);
  }
  return out;
}

const TERRAINS = {
  path: {
    rim: '#c9995f', fill: '#ddb27b', rimWidth: 4,
    detail: (seed) => speckles(seed, 9, ['#c79a62', '#e8c895', '#bf8f58'], 2.5, 5.5),
  },
  stone: {
    rim: '#b3aa98', fill: '#ddd5c4', rimWidth: 3,
    detail: () => {
      // Square paving with soft joints; joints at tile edges line up across tiles.
      let d = '';
      const j = '#c9c0ad';
      for (let i = 0; i <= 2; i++) {
        d += S.rect(0, i * 64 - 1.5, T, 3, j);
      }
      d += S.rect(-1.5, 0, 3, 64, j) + S.rect(64 - 1.5, 64, 3, 64, j) + S.rect(T - 1.5, 0, 3, 64, j);
      d += S.rect(6, 6, 52, 6, '#e8e2d5', { rx: 3 }) + S.rect(70, 70, 52, 6, '#e8e2d5', { rx: 3 });
      return d;
    },
  },
  soil: {
    rim: '#b98a54', fill: '#cc9f68', rimWidth: 4,
    detail: (seed) => speckles(seed, 14, ['#b88b56', '#d8b07c', '#a97c4a'], 1.8, 3.6),
  },
  water: {
    rim: '#e9f6ff', fill: '#5fb6df', rimWidth: 6,
    detail: (seed) => {
      const r = S.rng(seed);
      let d = '';
      for (let i = 0; i < 3; i++) {
        const x = 18 + r() * 80, y = 20 + r() * 88;
        d += S.path(`M${x},${y} q10,-5 20,0`, 'none', { stroke: '#a8def5', strokeWidth: 3, strokeLinecap: 'round', opacity: 0.8 });
      }
      return d;
    },
    inner: '#4a9fcc',
  },
};

function terrainTile(name, def, mask, variant = 0) {
  const id = `${name}${mask}`;
  const seed = mask * 97 + variant * 13 + name.length * 1000;
  let body = S.g(S.rect(-2, -2, T + 4, T + 4, def.rim), { clipPath: `url(#${id}o)` });
  let fill = S.rect(-2, -2, T + 4, T + 4, def.fill);
  if (def.inner) fill += S.g(S.rect(-2, -2, T + 4, T + 4, def.inner), { clipPath: `url(#${id}d)` });
  fill += def.detail(seed);
  body += S.g(fill, { clipPath: `url(#${id}n)` });
  return S.svg(T, T, body, clipDefs(id, mask, def.rimWidth));
}

function grassTile(variant) {
  const r = S.rng(31 + variant * 7);
  let body = S.rect(0, 0, T, T, '#7fbf5b');
  // Soft lighter and darker patches, then little blades.
  for (let i = 0; i < 3; i++) {
    const x = 20 + r() * 88, y = 20 + r() * 88;
    body += S.ellipse(x, y, 14 + r() * 10, 9 + r() * 6, r() > 0.5 ? '#88c763' : '#78b755', { opacity: 0.8 });
  }
  for (let i = 0; i < 7; i++) {
    const x = 12 + r() * 104, y = 14 + r() * 100;
    const c = r() > 0.5 ? '#6aa94c' : '#93cf6c';
    body += S.path(`M${x},${y} l-3,-7 M${x},${y} l1,-9 M${x},${y} l4,-6`, 'none',
      { stroke: c, strokeWidth: 2.2, strokeLinecap: 'round' });
  }
  return S.svg(T, T, body);
}

function floorTile(variant) {
  const r = S.rng(77 + variant);
  let body = S.rect(0, 0, T, T, '#d9a873');
  for (let i = 0; i < 4; i++) {
    const y = i * 32;
    body += S.rect(0, y, T, 32, i % 2 ? '#d4a26c' : '#dcad79');
    body += S.rect(0, y + 30, T, 2, '#b98552');
    const cut = 20 + r() * 88;
    body += S.rect(cut, y, 2, 30, '#c08c58');
    body += S.path(`M${8 + r() * 40},${y + 10 + r() * 10} q14,-3 28,0`, 'none', { stroke: '#c9955f', strokeWidth: 1.6, opacity: 0.7 });
  }
  return S.svg(T, T, body);
}

module.exports = function terrain() {
  const out = [];
  for (let v = 0; v < 4; v++) out.push({ name: `terrain/grass_${v}`, svg: grassTile(v) });
  for (let v = 0; v < 2; v++) out.push({ name: `terrain/floor_${v}`, svg: floorTile(v) });
  for (const [name, def] of Object.entries(TERRAINS)) {
    for (let mask = 1; mask < 16; mask++) out.push({ name: `terrain/${name}_${mask}`, svg: terrainTile(name, def, mask) });
    out.push({ name: `terrain/${name}_15b`, svg: terrainTile(name, def, 15, 1) });
  }
  return out;
};
