// Small helpers for writing SVG sprites by hand.
'use strict';

const TILE = 128; // pixels per world tile in the source art

function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }

function hexToRgb(hex) {
  const h = hex.replace('#', '');
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}
function rgbToHex([r, g, b]) {
  return '#' + [r, g, b].map((v) => clamp(Math.round(v), 0, 255).toString(16).padStart(2, '0')).join('');
}
/** Mixes a color toward white (amt > 0) or black (amt < 0). */
function shade(hex, amt) {
  const c = hexToRgb(hex);
  const t = amt > 0 ? 255 : 0;
  const k = Math.abs(amt);
  return rgbToHex(c.map((v) => v + (t - v) * k));
}
function mix(a, b, t) {
  const ca = hexToRgb(a), cb = hexToRgb(b);
  return rgbToHex(ca.map((v, i) => v + (cb[i] - v) * t));
}

function attrs(o) {
  return Object.entries(o)
    .filter(([, v]) => v !== undefined && v !== null && v !== false)
    .map(([k, v]) => `${k.replace(/[A-Z]/g, (m) => '-' + m.toLowerCase())}="${v}"`)
    .join(' ');
}
const el = (tag, o, inner) => (inner === undefined ? `<${tag} ${attrs(o)}/>` : `<${tag} ${attrs(o)}>${inner}</${tag}>`);
const rect = (x, y, w, h, fill, o = {}) => el('rect', { x, y, width: w, height: h, fill, ...o });
const circle = (cx, cy, r, fill, o = {}) => el('circle', { cx, cy, r, fill, ...o });
const ellipse = (cx, cy, rx, ry, fill, o = {}) => el('ellipse', { cx, cy, rx, ry, fill, ...o });
const path = (d, fill, o = {}) => el('path', { d, fill, ...o });
const g = (inner, o = {}) => el('g', o, Array.isArray(inner) ? inner.join('') : inner);
const line = (x1, y1, x2, y2, stroke, w, o = {}) => el('line', { x1, y1, x2, y2, stroke, strokeWidth: w, strokeLinecap: 'round', ...o });
const polygon = (pts, fill, o = {}) => el('polygon', { points: pts.map((p) => p.join(',')).join(' '), fill, ...o });

/** Soft ground shadow. */
const shadow = (cx, cy, rx, ry, a = 0.22) => ellipse(cx, cy, rx, ry, '#1d2a12', { opacity: a });

function linear(id, stops, x1 = 0, y1 = 0, x2 = 0, y2 = 1) {
  return el('linearGradient', { id, x1, y1, x2, y2 },
    stops.map(([o, c, a = 1]) => el('stop', { offset: o, stopColor: c, stopOpacity: a })).join(''));
}
function radial(id, stops, cx = 0.5, cy = 0.5, r = 0.5, fx, fy) {
  return el('radialGradient', { id, cx, cy, r, fx, fy },
    stops.map(([o, c, a = 1]) => el('stop', { offset: o, stopColor: c, stopOpacity: a })).join(''));
}

function svg(w, h, body, defs = '') {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">` +
    (defs ? `<defs>${defs}</defs>` : '') + (Array.isArray(body) ? body.join('') : body) + '</svg>';
}

/** Deterministic pseudo random numbers so the art is the same on every run. */
function rng(seed) {
  let s = seed >>> 0 || 1;
  return () => {
    s ^= s << 13; s >>>= 0;
    s ^= s >>> 17;
    s ^= s << 5; s >>>= 0;
    return (s >>> 0) / 4294967296;
  };
}

/** Rounded rectangle path. */
function rrect(x, y, w, h, r) {
  r = Math.min(r, w / 2, h / 2);
  return `M${x + r},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${y + h - r}Q${x + w},${y + h} ${x + w - r},${y + h}H${x + r}Q${x},${y + h} ${x},${y + h - r}V${y + r}Q${x},${y} ${x + r},${y}Z`;
}

/** A blob: smooth closed shape through points around a center (for bushes, canopies). */
function blob(cx, cy, r, n, wobble, rand, squash = 1) {
  const pts = [];
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2;
    const rr = r * (1 - wobble / 2 + rand() * wobble);
    pts.push([cx + Math.cos(a) * rr, cy + Math.sin(a) * rr * squash]);
  }
  return smoothClosed(pts);
}

/** Catmull-Rom spline through points, as a closed path. */
function smoothClosed(pts) {
  const n = pts.length;
  let d = `M${pts[0][0].toFixed(1)},${pts[0][1].toFixed(1)}`;
  for (let i = 0; i < n; i++) {
    const p0 = pts[(i - 1 + n) % n], p1 = pts[i], p2 = pts[(i + 1) % n], p3 = pts[(i + 2) % n];
    const c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
    const c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
    d += `C${c1[0].toFixed(1)},${c1[1].toFixed(1)} ${c2[0].toFixed(1)},${c2[1].toFixed(1)} ${p2[0].toFixed(1)},${p2[1].toFixed(1)}`;
  }
  return d + 'Z';
}

module.exports = {
  TILE, shade, mix, el, rect, circle, ellipse, path, g, line, polygon, shadow, linear, radial, svg, rng, rrect, blob,
  smoothClosed, attrs,
};
