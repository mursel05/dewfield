// Android launcher icons: legacy square/round PNGs and the adaptive icon layers.
'use strict';
const path = require('path');
const S = require('./svg');

const RES = path.join(__dirname, '../../../android/src/main/res');
const DENS = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };

// Artwork on a 108x108 adaptive canvas (the safe zone is the middle 66).
function art(withBg) {
  let d = '';
  if (withBg) d += S.rect(0, 0, 108, 108, 'url(#sky)');
  d += S.circle(78, 30, 10, '#ffd23f', { stroke: '#f6b733', strokeWidth: 2 });
  d += S.path('M0,78 Q30,60 54,66 Q80,72 108,62 V108 H0 Z', '#7fc25a');
  d += S.path('M0,88 Q40,76 108,84 V108 H0 Z', '#6aac4b');
  d += S.ellipse(54, 80, 18, 6, '#9c6b3f');
  d += S.path('M54,80 V58', 'none', { stroke: '#3e7f2f', strokeWidth: 4, strokeLinecap: 'round' });
  d += S.path('M54,62 Q40,40 26,44 Q30,62 54,64 Z', '#6cbf4f', { stroke: '#3e7f2f', strokeWidth: 2.5 });
  d += S.path('M54,58 Q66,34 84,36 Q80,58 54,60 Z', '#82cf63', { stroke: '#3e7f2f', strokeWidth: 2.5 });
  return d;
}
const defs = S.linear('sky', [[0, '#bfe7ff'], [1, '#e9f7d8']]);

module.exports = function appIcons() {
  const out = [];
  for (const [dn, k] of Object.entries(DENS)) {
    const legacy = Math.round(48 * k);
    const fg = Math.round(108 * k);
    const square = `<svg xmlns="http://www.w3.org/2000/svg" width="${legacy}" height="${legacy}" viewBox="0 0 108 108"><defs>${defs}<clipPath id="c"><rect x="6" y="6" width="96" height="96" rx="22"/></clipPath></defs><g clip-path="url(#c)">${art(true)}</g></svg>`;
    const round = `<svg xmlns="http://www.w3.org/2000/svg" width="${legacy}" height="${legacy}" viewBox="0 0 108 108"><defs>${defs}<clipPath id="c"><circle cx="54" cy="54" r="50"/></clipPath></defs><g clip-path="url(#c)">${art(true)}</g></svg>`;
    const fore = `<svg xmlns="http://www.w3.org/2000/svg" width="${fg}" height="${fg}" viewBox="0 0 108 108">${art(false)}</svg>`;
    const back = `<svg xmlns="http://www.w3.org/2000/svg" width="${fg}" height="${fg}" viewBox="0 0 108 108"><defs>${defs}</defs>${S.rect(0, 0, 108, 108, 'url(#sky)')}</svg>`;
    out.push({ name: `app/${dn}/ic_launcher`, svg: square, out: path.join(RES, `mipmap-${dn}`, 'ic_launcher.png') });
    out.push({ name: `app/${dn}/ic_launcher_round`, svg: round, out: path.join(RES, `mipmap-${dn}`, 'ic_launcher_round.png') });
    out.push({ name: `app/${dn}/fg`, svg: fore, out: path.join(RES, `mipmap-${dn}`, 'ic_launcher_foreground.png') });
    out.push({ name: `app/${dn}/bg`, svg: back, out: path.join(RES, `mipmap-${dn}`, 'ic_launcher_background.png') });
  }
  out.push({ name: 'app/store', svg: `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108"><defs>${defs}</defs>${art(true)}</svg>`, out: path.join(__dirname, '../../../docs/icon-512.png') });
  return out;
};
