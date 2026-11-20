// Extrae los TECHOS de BUILDINGS/IPN/building_escom.webp como POLÍGONOS DE N LADOS (contorno real, no rectángulos).
// Se evalúa por DevTools con el juego abierto (ver art_to_footprints.py). Devuelve JSON con coordenadas NORMALIZADAS
// de la imagen (0..1). Pasos:
//   1. clasifica celdas de CELL px: techo naranja (+ paneles azules que tocan techo);
//   2. cierre/apertura morfológica de radio ~0.9 m (cubre juntas; ⚠️ con radio ~5 m se rellenaban los pasillos entre alas);
//   3. rellena huecos pequeños y PARTE en franjas verticales por el centro de cada patio grande, para que cada pieza
//      sea un polígono simple sin agujeros (la colisión es de anillo exterior);
//   4. traza el contorno de cada pieza y lo simplifica (Douglas-Peucker) a unos pocos vértices.
(function () {
  var im = Array.prototype.slice.call(document.querySelectorAll('img')).filter(function (i) { return /building_escom\.webp/.test(i.src); })[0];
  var W = im.naturalWidth, H = im.naturalHeight;
  var c = document.createElement('canvas'); c.width = W; c.height = H; var x = c.getContext('2d'); x.drawImage(im, 0, 0);
  var d = x.getImageData(0, 0, W, H).data;
  var CELL = 4, gw = Math.floor(W / CELL), gh = Math.floor(H / CELL), N = gw * gh;
  var CLOSE_R = 2, OPEN_R = 2;           // radios en celdas (~0.9 m con CELL 4)
  var MIN_HOLE_CELLS = 400;             // huecos menores (~80 m²) se rellenan; los mayores son patios
  var RDP_EPS = 1.6;                    // tolerancia de simplificación en celdas (~0.7 m)
  var MIN_AREA_M2 = 1000.0, M_PER_CELL_X = 212.7 * 0.9854224 * CELL / W, M_PER_CELL_Y = 263.0 * 0.9854224 * CELL / H;

  var O = new Uint8Array(N), B = new Uint8Array(N);
  for (var gy = 0; gy < gh; gy++) for (var gx = 0; gx < gw; gx++) {
    var no = 0, nb = 0, n = 0;
    for (var yy = gy * CELL; yy < (gy + 1) * CELL; yy++) for (var xx = gx * CELL; xx < (gx + 1) * CELL; xx++) {
      var k = (yy * W + xx) * 4, R = d[k], G = d[k + 1], Bl = d[k + 2], A = d[k + 3]; n++;
      if (A > 40 && R > 120 && R - G > 40 && R - Bl > 50) no++;
      else if (A > 40 && Bl > R + 12 && Bl > G - 5 && Bl > 90) nb++;
    }
    if (no / n > 0.35) O[gy * gw + gx] = 1;
    if (nb / n > 0.5) B[gy * gw + gx] = 1;
  }
  function dilate(m, r) { var o = new Uint8Array(N); for (var y = 0; y < gh; y++) for (var xx = 0; xx < gw; xx++) { if (!m[y * gw + xx]) continue; for (var dy = -r; dy <= r; dy++) { var ny = y + dy; if (ny < 0 || ny >= gh) continue; for (var dx = -r; dx <= r; dx++) { var nx = xx + dx; if (nx >= 0 && nx < gw) o[ny * gw + nx] = 1; } } } return o; }
  function erode(m, r) { var o = new Uint8Array(N); for (var y = 0; y < gh; y++) for (var xx = 0; xx < gw; xx++) { var ok = 1; for (var dy = -r; dy <= r && ok; dy++) { var ny = y + dy; if (ny < 0 || ny >= gh) { ok = 0; break; } for (var dx = -r; dx <= r; dx++) { var nx = xx + dx; if (nx < 0 || nx >= gw || !m[ny * gw + nx]) { ok = 0; break; } } } o[y * gw + xx] = ok; } return o; }
  var nearO = dilate(O, 16);
  var roof = new Uint8Array(N); for (var i = 0; i < N; i++) roof[i] = (O[i] || (B[i] && nearO[i])) ? 1 : 0;
  roof = erode(dilate(roof, CLOSE_R), CLOSE_R);
  roof = dilate(erode(roof, OPEN_R), OPEN_R);

  // componentes (4-conexo) de una máscara; devuelve etiquetas y tamaños
  function label(m, target) {
    var lab = new Int32Array(N), sizes = [0], cnt = 0, stack = [];
    for (var s = 0; s < N; s++) {
      if (m[s] !== target || lab[s]) continue;
      cnt++; lab[s] = cnt; stack.push(s); var sz = 0;
      while (stack.length) {
        var p = stack.pop(); sz++; var py = (p / gw) | 0, px = p - py * gw;
        if (px > 0 && m[p - 1] === target && !lab[p - 1]) { lab[p - 1] = cnt; stack.push(p - 1); }
        if (px < gw - 1 && m[p + 1] === target && !lab[p + 1]) { lab[p + 1] = cnt; stack.push(p + 1); }
        if (py > 0 && m[p - gw] === target && !lab[p - gw]) { lab[p - gw] = cnt; stack.push(p - gw); }
        if (py < gh - 1 && m[p + gw] === target && !lab[p + gw]) { lab[p + gw] = cnt; stack.push(p + gw); }
      }
      sizes.push(sz);
    }
    return { lab: lab, sizes: sizes, count: cnt };
  }

  // huecos (fondo que no toca el borde): rellena los pequeños, anota el centro de los grandes
  var bg = label(roof, 0), touchesBorder = {}, holeBox = {};
  for (var q = 0; q < N; q++) {
    var l = bg.lab[q]; if (!l) continue; var qy = (q / gw) | 0, qx = q - qy * gw;
    if (qx === 0 || qy === 0 || qx === gw - 1 || qy === gh - 1) touchesBorder[l] = true;
    var hb = holeBox[l] || (holeBox[l] = [qx, qy, qx, qy]);
    if (qx < hb[0]) hb[0] = qx; if (qy < hb[1]) hb[1] = qy; if (qx > hb[2]) hb[2] = qx; if (qy > hb[3]) hb[3] = qy;
  }
  var cuts = [];
  for (var h = 1; h <= bg.count; h++) {
    if (touchesBorder[h]) continue;
    if (bg.sizes[h] < MIN_HOLE_CELLS) { for (var q2 = 0; q2 < N; q2++) if (bg.lab[q2] === h) roof[q2] = 1; }
    else cuts.push(((holeBox[h][0] + holeBox[h][2]) / 2) | 0);
  }
  cuts.sort(function (a, b) { return a - b; });

  // franjas verticales: cada pieza queda sin agujeros
  var piecesMask = [];
  var edges = [-1].concat(cuts, [gw]);
  for (var e = 0; e < edges.length - 1; e++) {
    var pm = new Uint8Array(N), any = false;
    for (var y2 = 0; y2 < gh; y2++) for (var x2 = edges[e] + 1; x2 <= edges[e + 1] && x2 < gw; x2++) if (roof[y2 * gw + x2]) { pm[y2 * gw + x2] = 1; any = true; }
    if (any) piecesMask.push(pm);
  }

  // contorno de una componente: aristas dirigidas con el techo a la derecha, unidas en un lazo
  function trace(mask, lab, id) {
    var next = {}; // "x,y" -> lista de [x2,y2]
    function add(ax, ay, bx, by) { var k = ax + ',' + ay; (next[k] || (next[k] = [])).push([bx, by]); }
    for (var y = 0; y < gh; y++) for (var xx = 0; xx < gw; xx++) {
      if (lab[y * gw + xx] !== id) continue;
      var up = y === 0 || lab[(y - 1) * gw + xx] !== id, dn = y === gh - 1 || lab[(y + 1) * gw + xx] !== id;
      var lf = xx === 0 || lab[y * gw + xx - 1] !== id, rt = xx === gw - 1 || lab[y * gw + xx + 1] !== id;
      if (up) add(xx, y, xx + 1, y);          // arista superior hacia la derecha
      if (rt) add(xx + 1, y, xx + 1, y + 1);  // derecha hacia abajo
      if (dn) add(xx + 1, y + 1, xx, y + 1);  // inferior hacia la izquierda
      if (lf) add(xx, y + 1, xx, y);          // izquierda hacia arriba
    }
    var loops = [], used = {};
    Object.keys(next).forEach(function (startKey) {
      while (next[startKey] && next[startKey].length) {
        var sx = +startKey.split(',')[0], sy = +startKey.split(',')[1], loop = [[sx, sy]], cx = sx, cy = sy, px = -1, py = -1, guard = 0;
        do {
          var k2 = cx + ',' + cy, opts = next[k2];
          if (!opts || !opts.length) break;
          var pick = 0;
          if (opts.length > 1) { // vértice en silla: gira siempre al mismo lado para no cruzar
            var best = -1e9; for (var oi = 0; oi < opts.length; oi++) { var dx = opts[oi][0] - cx, dy = opts[oi][1] - cy, ix = cx - px, iy = cy - py; var cross = ix * dy - iy * dx; if (cross > best) { best = cross; pick = oi; } }
          }
          var nx = opts[pick][0], ny = opts[pick][1]; opts.splice(pick, 1);
          px = cx; py = cy; cx = nx; cy = ny; loop.push([cx, cy]); guard++;
        } while ((cx !== sx || cy !== sy) && guard < 200000);
        loops.push(loop);
      }
    });
    return loops;
  }
  function polyArea(p) { var a = 0; for (var i = 0; i < p.length - 1; i++) a += p[i][0] * p[i + 1][1] - p[i + 1][0] * p[i][1]; return a / 2; }
  function rdp(pts, eps) {
    if (pts.length < 3) return pts;
    var a = pts[0], b = pts[pts.length - 1], dmax = 0, idx = 0, dx = b[0] - a[0], dy = b[1] - a[1], len = Math.sqrt(dx * dx + dy * dy) || 1;
    for (var i = 1; i < pts.length - 1; i++) { var dd = Math.abs((pts[i][0] - a[0]) * dy - (pts[i][1] - a[1]) * dx) / len; if (dd > dmax) { dmax = dd; idx = i; } }
    if (dmax > eps) { var l1 = rdp(pts.slice(0, idx + 1), eps), l2 = rdp(pts.slice(idx), eps); return l1.slice(0, -1).concat(l2); }
    return [a, b];
  }
  function simplifyLoop(loop) { // lazo cerrado: parte en dos mitades para que RDP no colapse
    var pts = loop.slice(0, -1); if (pts.length < 4) return pts;
    var half = (pts.length / 2) | 0;
    var a = rdp(pts.slice(0, half + 1), RDP_EPS), b = rdp(pts.slice(half).concat([pts[0]]), RDP_EPS);
    var out = a.slice(0, -1).concat(b.slice(0, -1));
    // quita colineales exactos
    var res = []; for (var i = 0; i < out.length; i++) { var p0 = out[(i + out.length - 1) % out.length], p1 = out[i], p2 = out[(i + 1) % out.length]; if ((p1[0] - p0[0]) * (p2[1] - p1[1]) - (p1[1] - p0[1]) * (p2[0] - p1[0]) !== 0) res.push(p1); }
    return res;
  }

  var polys = [], stats = { pieces: piecesMask.length, holesFilled: 0, cuts: cuts.length };
  piecesMask.forEach(function (pm) {
    var comp = label(pm, 1);
    for (var id = 1; id <= comp.count; id++) {
      var areaM2 = comp.sizes[id] * M_PER_CELL_X * M_PER_CELL_Y;
      if (areaM2 < MIN_AREA_M2 * 0.05) continue; // descarta manchas mínimas; el filtro fino por área total se hace en Python
      var loops = trace(pm, comp.lab, id);
      loops.forEach(function (lp) {
        if (polyArea(lp) <= 0) return; // lazos de agujero (orientación contraria): ya se rellenaron o partieron
        var s = simplifyLoop(lp); if (s.length < 3) return;
        polys.push({ areaM2: +areaM2.toFixed(1), pts: s.map(function (p) { return [+(p[0] * CELL / W).toFixed(5), +(p[1] * CELL / H).toFixed(5)]; }) });
      });
    }
  });
  return JSON.stringify({ W: W, H: H, cell: CELL, stats: stats, count: polys.length, polys: polys });
})()
