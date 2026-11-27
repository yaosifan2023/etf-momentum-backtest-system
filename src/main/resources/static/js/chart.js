/* SVG 折线图:按容器实际宽度绘制;支持虚线序列、键盘与触屏查值、容器尺寸变化时重绘 */
"use strict";

/**
 * renderLineChart(container, {
 *   series: [{ name, color, points: [{ label, value }], dash: true }],
 *   height: 340,             // 画布高度(px)
 *   yFmt: v => String(v),    // Y 轴与提示中的数值格式
 *   xLabel: s => String(s),  // X 轴短刻度;提示中始终显示完整 label
 *   legend: true,            // 是否绘制图例(页面自绘图例时传 false)
 *   base: 1                  // 水平参考线;多序列默认为 1,传 null 不画
 * })
 */
function renderLineChart(container, opts) {
  const series = (opts.series || []).filter(s => s && s.points && s.points.length);
  if (!series.length) {
    container.innerHTML = '<div class="empty">暂无绘图数据</div>';
    return;
  }
  const state = { opts, series, idx: -1, w: 0 };
  container.__chart = state;
  if (!container.__chartRO) {
    if (typeof ResizeObserver !== "undefined") {
      container.__chartRO = new ResizeObserver(() => {
        if (container.__chartRaf) return;
        container.__chartRaf = requestAnimationFrame(() => {
          container.__chartRaf = 0;
          const st = container.__chart;
          if (st && Math.floor(container.clientWidth) !== st.w) draw(container, st);
        });
      });
      container.__chartRO.observe(container);
    }
  }
  draw(container, state);
}

function isVal(v) { return v !== null && v !== undefined && isFinite(v); }

function draw(container, st) {
  const opts = st.opts, series = st.series;
  const W = Math.max(Math.floor(container.clientWidth || 0), 160);
  const H = opts.height || 300;
  const n = series[0].points.length;
  const pad = W < 480 ? { l: 42, r: 10, t: 12, b: 26 } : { l: 52, r: 16, t: 14, b: 28 };
  const pw = W - pad.l - pad.r, ph = H - pad.t - pad.b;
  st.w = W;

  let minY = Infinity, maxY = -Infinity;
  for (const s of series) {
    for (const p of s.points) {
      if (!isVal(p.value)) continue;
      if (p.value < minY) minY = p.value;
      if (p.value > maxY) maxY = p.value;
    }
  }
  if (!isFinite(minY)) {
    container.innerHTML = '<div class="empty">暂无绘图数据</div>';
    return;
  }
  if (maxY - minY < 1e-12) { maxY += 0.005; minY -= 0.005; }
  const span = maxY - minY;
  minY -= span * 0.06;
  maxY += span * 0.06;

  const x = i => pad.l + pw * (n === 1 ? 0.5 : i / (n - 1));
  const y = v => pad.t + ph * (1 - (v - minY) / (maxY - minY));
  const yFmt = opts.yFmt || (v => v.toFixed(3));
  const xLabel = opts.xLabel || (s => s);
  const GRID = "rgb(20 42 64 / 9%)", TEXT = "#516579";

  let svg = '<svg class="chart-svg" viewBox="0 0 ' + W + " " + H + '" width="100%" height="' + H
    + '" tabindex="0" role="img" aria-label="折线图,可用左右方向键查看每日数值">';
  // 横向细网格 + Y 轴刻度
  for (let t = 0; t <= 5; t++) {
    const v = minY + (maxY - minY) * t / 5;
    const yy = y(v).toFixed(1);
    svg += '<line x1="' + pad.l + '" y1="' + yy + '" x2="' + (W - pad.r) + '" y2="' + yy + '" stroke="' + GRID + '"/>';
    svg += '<text x="' + (pad.l - 8) + '" y="' + (y(v) + 4).toFixed(1) + '" text-anchor="end" font-size="11.5" fill="' + TEXT + '">'
      + esc(yFmt(v)) + "</text>";
  }
  // X 轴短刻度:数量随宽度变化,空间不足时自动减少
  const count = Math.max(2, Math.min(8, Math.floor(pw / 84)));
  const ticks = [];
  for (let t = 0; t < count; t++) {
    const i = Math.round(t * (n - 1) / (count - 1));
    if (ticks.indexOf(i) < 0) ticks.push(i);
  }
  ticks.forEach((i, k) => {
    const first = k === 0, last = k === ticks.length - 1;
    const anchor = n > 1 && first ? "start" : (n > 1 && last ? "end" : "middle");
    const tx = n > 1 && first ? pad.l : (n > 1 && last ? W - pad.r : x(i));
    svg += '<text x="' + tx.toFixed(1) + '" y="' + (H - 7) + '" text-anchor="' + anchor
      + '" font-size="11.5" fill="' + TEXT + '">' + esc(xLabel(series[0].points[i].label)) + "</text>";
  });
  // 水平参考线
  const base = opts.base === undefined ? (series.length > 1 ? 1 : null) : opts.base;
  if (base !== null && base > minY && base < maxY) {
    const by = y(base).toFixed(1);
    svg += '<line x1="' + pad.l + '" y1="' + by + '" x2="' + (W - pad.r) + '" y2="' + by
      + '" stroke="rgb(20 42 64 / 24%)" stroke-dasharray="5 5"/>';
  }
  // 数据线:实线与虚线区分,不只依赖颜色
  series.forEach((s, si) => {
    let d = "", started = false;
    s.points.forEach((p, i) => {
      if (!isVal(p.value)) { started = false; return; }
      d += (started ? " L " : " M ") + x(i).toFixed(2) + " " + y(p.value).toFixed(2);
      started = true;
    });
    svg += '<path d="' + d + '" fill="none" stroke="' + s.color + '" stroke-width="' + (si === 0 ? 2.2 : 1.8)
      + '" stroke-linejoin="round" stroke-linecap="round"' + (s.dash ? ' stroke-dasharray="7 5"' : "") + "/>";
  });
  // 十字线与数据点
  svg += '<g class="xh" visibility="hidden"><line y1="' + pad.t + '" y2="' + (pad.t + ph) + '" stroke="rgb(20 42 64 / 45%)"/>';
  series.forEach(s => { svg += '<circle r="3.5" fill="#fff" stroke="' + s.color + '" stroke-width="2"/>'; });
  svg += "</g></svg>";

  let legend = "";
  if (opts.legend !== false) {
    legend = '<div class="chart-legend">' + series.map(s =>
      '<span class="item"><span class="swatch' + (s.dash ? " dashed" : "") + '" style="color:' + s.color + '"></span>'
      + esc(s.name) + "</span>").join("") + "</div>";
  }
  container.innerHTML = '<div class="chart-box">' + legend
    + '<div class="chart-area" style="position:relative">' + svg + '<div class="chart-tip"></div></div></div>';

  const svgEl = container.querySelector("svg");
  const tip = container.querySelector(".chart-tip");
  const area = container.querySelector(".chart-area");
  const xh = container.querySelector(".xh");
  const line = xh.querySelector("line");
  const dots = xh.querySelectorAll("circle");

  function hide() {
    xh.setAttribute("visibility", "hidden");
    tip.style.display = "none";
    st.idx = -1;
  }
  function show(idx) {
    idx = Math.max(0, Math.min(n - 1, idx));
    st.idx = idx;
    const scale = (svgEl.getBoundingClientRect().width || W) / W;
    const cx = x(idx);
    line.setAttribute("x1", cx);
    line.setAttribute("x2", cx);
    let rows = "", spoken = [];
    series.forEach((s, i) => {
      const v = s.points[idx].value, ok = isVal(v);
      if (ok) {
        dots[i].setAttribute("cx", cx);
        dots[i].setAttribute("cy", y(v));
        dots[i].setAttribute("visibility", "visible");
      } else {
        dots[i].setAttribute("visibility", "hidden");
      }
      rows += '<div><span class="swatch' + (s.dash ? " dashed" : "") + '" style="color:' + s.color + '"></span>'
        + esc(s.name) + (ok ? ":" + yFmt(v) : ":NA") + "</div>";
      spoken.push(s.name + " " + (ok ? yFmt(v) : "NA"));
    });
    xh.setAttribute("visibility", "visible");
    const label = String(series[0].points[idx].label);
    tip.innerHTML = "<b>" + esc(label) + "</b>" + rows;
    tip.style.display = "block";
    // 提示尽量留在画板内
    const aw = area.clientWidth || W, tw = tip.offsetWidth;
    let left = cx * scale + 14;
    if (left + tw > aw - 4) left = cx * scale - tw - 14;
    tip.style.left = Math.max(4, left) + "px";
    tip.style.top = pad.t + "px";
    svgEl.setAttribute("aria-label", label + "," + spoken.join(","));
  }
  function idxAt(clientX) {
    const rect = svgEl.getBoundingClientRect();
    const px = (clientX - rect.left) * (W / (rect.width || W));
    return Math.round((px - pad.l) / (pw / Math.max(n - 1, 1)));
  }

  svgEl.addEventListener("mousemove", ev => show(idxAt(ev.clientX)));
  svgEl.addEventListener("mouseleave", () => { if (document.activeElement !== svgEl) hide(); });
  svgEl.addEventListener("focus", () => show(st.idx >= 0 ? st.idx : n - 1));
  svgEl.addEventListener("blur", hide);
  svgEl.addEventListener("keydown", ev => {
    let i = st.idx >= 0 ? st.idx : n - 1;
    if (ev.key === "ArrowLeft") i--;
    else if (ev.key === "ArrowRight") i++;
    else if (ev.key === "Home") i = 0;
    else if (ev.key === "End") i = n - 1;
    else return;
    ev.preventDefault();
    show(i);
  });
  svgEl.addEventListener("pointerdown", ev => {
    if (ev.pointerType === "touch") show(idxAt(ev.clientX));
  });

  if (st.idx >= 0) show(st.idx);   // 尺寸变化重绘时保留当前位置
}

function esc(s) {
  return String(s === null || s === undefined ? "" : s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}
