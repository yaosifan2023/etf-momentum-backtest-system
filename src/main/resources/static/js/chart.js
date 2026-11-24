/* 轻量 SVG 折线图:无外部依赖,支持悬停十字线与数值提示 */
"use strict";

/**
 * opts = {
 *   series: [{name, color, points: [{label, value}]}],  // 各序列 points 长度一致
 *   height: 320, yFmt: fn(v)->string
 * }
 */
function renderLineChart(container, opts) {
  const series = opts.series || [];
  if (!series.length || !series[0].points || series[0].points.length === 0) {
    container.innerHTML = '<div class="callout warn" style="margin:0">暂无绘图数据</div>';
    return;
  }
  const W = Math.max(container.clientWidth || 860, 480);
  const H = opts.height || 320;
  const pad = { l: 62, r: 18, t: 14, b: 30 };
  const n = series[0].points.length;

  let minY = Infinity, maxY = -Infinity, hasVal = false;
  for (const s of series) {
    for (const p of s.points) {
      if (p.value !== null && p.value !== undefined && isFinite(p.value)) {
        hasVal = true;
        if (p.value < minY) minY = p.value;
        if (p.value > maxY) maxY = p.value;
      }
    }
  }
  if (!hasVal) {
    container.innerHTML = '<div class="callout warn" style="margin:0">暂无绘图数据</div>';
    return;
  }
  if (maxY - minY < 1e-12) { maxY += 0.005; minY -= 0.005; }
  const span = maxY - minY;
  minY -= span * 0.05; maxY += span * 0.05;

  const x = i => pad.l + (W - pad.l - pad.r) * (n === 1 ? 0 : i / (n - 1));
  const y = v => pad.t + (H - pad.t - pad.b) * (1 - (v - minY) / (maxY - minY));
  const yFmt = opts.yFmt || (v => v.toFixed(3));

  let svg = '<svg viewBox="0 0 ' + W + " " + H + '" width="100%" height="' + H + '" style="display:block">';
  // 网格与Y轴刻度
  const ticks = 5;
  for (let t = 0; t <= ticks; t++) {
    const v = minY + (maxY - minY) * (t / ticks);
    const yy = y(v);
    svg += '<line x1="' + pad.l + '" y1="' + yy.toFixed(1) + '" x2="' + (W - pad.r) + '" y2="' + yy.toFixed(1)
      + '" stroke="#e4ece8" stroke-width="1"/>';
    svg += '<text x="' + (pad.l - 8) + '" y="' + (yy + 4).toFixed(1) + '" text-anchor="end" font-size="10.5" fill="#526778">'
      + esc(yFmt(v)) + "</text>";
  }
  // X轴刻度(约8个)
  const xStep = Math.max(1, Math.ceil(n / 8));
  for (let i = 0; i < n; i += xStep) {
    svg += '<text x="' + x(i).toFixed(1) + '" y="' + (H - 8) + '" text-anchor="middle" font-size="10.5" fill="#526778">'
      + esc(String(series[0].points[i].label)) + "</text>";
  }
  // 基准线 y=0 或首值
  const base = series.length > 1 ? 1 : series[0].points[0].value;
  if (base > minY && base < maxY) {
    svg += '<line x1="' + pad.l + '" y1="' + y(base).toFixed(1) + '" x2="' + (W - pad.r) + '" y2="' + y(base).toFixed(1)
      + '" stroke="#c8d6d0" stroke-width="1" stroke-dasharray="4 4"/>';
  }
  // 折线
  for (const s of series) {
    let d = "";
    let started = false;
    for (let i = 0; i < s.points.length; i++) {
      const v = s.points[i].value;
      if (v === null || v === undefined || !isFinite(v)) { started = false; continue; }
      d += (started ? " L " : " M ") + x(i).toFixed(2) + " " + y(v).toFixed(2);
      started = true;
    }
    svg += '<path d="' + d + '" fill="none" stroke="' + s.color + '" stroke-width="1.8" stroke-linejoin="round"/>';
  }
  // 悬停竖线(默认隐藏)
  svg += '<line id="__hoverline" x1="0" y1="' + pad.t + '" x2="0" y2="' + (H - pad.b)
    + '" stroke="#8aa39b" stroke-width="1" stroke-dasharray="3 3" visibility="hidden"/>';
  svg += "</svg>";

  let legend = '<div class="chart-legend">';
  for (const s of series) {
    legend += '<span class="item"><span class="swatch" style="background:' + s.color + '"></span>'
      + esc(s.name) + "</span>";
  }
  legend += "</div>";

  container.innerHTML = '<div class="chart-box">' + legend
    + '<div style="position:relative">' + svg + '<div class="chart-tip" id="__charttip"></div></div></div>';

  // 悬停交互
  const svgEl = container.querySelector("svg");
  const hoverLine = container.querySelector("#__hoverline");
  const tip = container.querySelector("#__charttip");
  const inner = container.querySelector(".chart-box > div:last-child");
  svgEl.addEventListener("mousemove", ev => {
    const rect = svgEl.getBoundingClientRect();
    const px = (ev.clientX - rect.left) * (W / rect.width);
    if (px < pad.l - 4 || px > W - pad.r + 4) { hoverLine.setAttribute("visibility", "hidden"); tip.style.display = "none"; return; }
    let idx = Math.round((px - pad.l) / ((W - pad.l - pad.r) / Math.max(n - 1, 1)));
    idx = Math.max(0, Math.min(n - 1, idx));
    const label = series[0].points[idx].label;
    let rows = "";
    for (const s of series) {
      const v = s.points[idx].value;
      rows += '<div><span style="display:inline-block;width:9px;height:9px;border-radius:2px;background:'
        + s.color + ';margin-right:5px"></span>' + esc(s.name) + ":"
        + (v === null || v === undefined ? "NA" : yFmt(v)) + "</div>";
    }
    tip.innerHTML = "<b>" + esc(String(label)) + "</b>" + rows;
    tip.style.display = "block";
    const innerRect = inner.getBoundingClientRect();
    let left = ev.clientX - innerRect.left + 14;
    if (left + 170 > innerRect.width) left = left - 190;
    tip.style.left = left + "px";
    tip.style.top = Math.max(4, ev.clientY - innerRect.top - 30) + "px";
    const xv = x(idx);
    hoverLine.setAttribute("x1", xv);
    hoverLine.setAttribute("x2", xv);
    hoverLine.setAttribute("visibility", "visible");
  });
  svgEl.addEventListener("mouseleave", () => {
    hoverLine.setAttribute("visibility", "hidden");
    tip.style.display = "none";
  });
}

function esc(s) {
  return String(s === null || s === undefined ? "" : s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}
