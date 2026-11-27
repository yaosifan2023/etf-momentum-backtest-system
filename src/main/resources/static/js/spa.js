/* ETF 动量轮动回测系统 · Vue 3 SPA(哈希路由 + 视图组件 + AI 助手) */
"use strict";

/* ================= 工具 ================= */
async function api(url, opts) {
  const init = Object.assign({ headers: { "Content-Type": "application/json" } }, opts || {});
  const res = await fetch(url, init);
  let body = null;
  try { body = await res.json(); } catch (e) { /* 非 JSON */ }
  if (!res.ok) throw new Error((body && body.message) ? body.message : ("请求失败 HTTP " + res.status));
  return body;
}
function esc(s) {
  return String(s === null || s === undefined ? "" : s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}
function fmtPct(v, d) {
  if (v === null || v === undefined || isNaN(v)) return "NA";
  return (v * 100).toFixed(d === undefined ? 2 : d) + "%";
}
function fmtNum(v, d) {
  if (v === null || v === undefined || isNaN(v)) return "NA";
  return (+v).toFixed(d === undefined ? 4 : d);
}
function fmtMoney(v) {
  if (v === null || v === undefined || isNaN(v)) return "NA";
  return (+v).toLocaleString("zh-CN", { maximumFractionDigits: 2, minimumFractionDigits: 2 });
}
function fmtQty(v) {
  if (v === null || v === undefined || isNaN(v)) return "NA";
  return String(Math.round(+v));
}
function signedClass(v) { return v > 0 ? "pos" : (v < 0 ? "neg" : ""); }

function toast(msg, type) {
  let box = document.getElementById("toasts");
  if (!box) { box = document.createElement("div"); box.id = "toasts"; document.body.appendChild(box); }
  const el = document.createElement("div");
  el.className = "toast " + (type || "");
  el.textContent = msg;
  box.appendChild(el);
  setTimeout(() => el.remove(), 4200);
}

const MODAL = Vue.reactive({ open: false, title: "", body: "", width: null, trigger: null });
function openModal(title, body, width) {
  MODAL.trigger = document.activeElement;   // 关闭后焦点回到触发入口
  MODAL.title = title; MODAL.body = body; MODAL.width = width || null; MODAL.open = true;
}
function closeModal() {
  MODAL.open = false;
  if (MODAL.trigger && typeof MODAL.trigger.focus === "function") MODAL.trigger.focus();
  MODAL.trigger = null;
}
/* 收起页面上的下拉菜单与说明浮层 */
function closeMenus() {
  document.querySelectorAll("details.menu[open]").forEach(d => { d.open = false; });
}
function statusText(s) { return s === "SUCCESS" ? "成功" : (s === "FAILED" ? "失败" : "运行中"); }

/* 轻量 Markdown:加粗 / 列表 / 换行 */
function mdLite(s) {
  let t = esc(String(s === null || s === undefined ? "" : s));
  t = t.replace(/\*\*(.+?)\*\*/g, "<b>$1</b>");
  t = t.replace(/^[-•]\s?(.*)$/gm, "&bull; $1");
  t = t.replace(/\n/g, "<br>");
  return t;
}

const METRIC_LABELS = {
  total_return: "累计收益", annual_return: "年化收益", annual_vol: "年化波动",
  max_drawdown: "最大回撤", sharpe: "夏普比率", turnover: "换手率(年)",
  trade_count: "成交笔数", total_cost: "总手续费(元)"
};
const METRIC_FMT = {
  total_return: v => fmtPct(v, 2), annual_return: v => fmtPct(v, 2),
  annual_vol: v => fmtPct(v, 2), max_drawdown: v => fmtPct(v, 2),
  sharpe: v => fmtNum(v, 3), turnover: v => fmtNum(v, 2),
  trade_count: v => fmtQty(v), total_cost: v => fmtMoney(v)
};
const helpers = { methods: { fmtPct, fmtNum, fmtMoney, fmtQty, signedClass,
  aiAnalyze(q) { this.$root.openAi(); this.$root.sendAi(q); } } };

/* AI 工具桥:各视图把可被 AI 修改的表单注册到这里,AI 面板通过它执行白名单操作 */
const AI_BUS = Vue.reactive({ strategyForm: null, backtestForm: null });

/* 工具风险分级:L2 持久化/资源操作需要用户确认后才执行 */
const L2_TOOLS = new Set(["save_and_run_backtest", "run_sample_check", "refresh_market_data", "add_etf"]);
const TOOL_LABELS = {
  update_strategy_form: "修改策略表单参数", set_backtest_dates: "设置回测日期",
  save_and_run_backtest: "保存策略并运行回测", run_sample_check: "运行 B 样本复算",
  refresh_market_data: "联网抓取行情", add_etf: "添加 ETF 并抓取",
  navigate: "切换页面", get_run_detail: "查询运行详情",
  check_data_quality: "行情数据体检(服务端)", analyze_run: "回测深度诊断(服务端)",
  generate_report: "生成研究报告(服务端)"
};

/* ================= 视图:概览 ================= */
const OverviewView = {
  mixins: [helpers],
  template: `
  <div>
    <section class="hero" aria-label="研究课题">
      <div>
        <h2>ETF 动量轮动</h2>
        <p v-if="error">概览加载失败：{{ error }}</p>
        <p v-else>对候选 ETF 按相对动量排名，回测轮动策略扣除交易成本后的表现。</p>
        <div class="hero-actions">
          <button class="btn" :disabled="loading" @click="primaryAction">{{ error ? '重试' : (loading ? '加载中…' : primaryLabel) }}</button>
          <a class="btn ghost" href="#/history">查看运行历史</a>
        </div>
      </div>
      <div class="hero-side">
        <div class="hs-row"><span class="hs-k">行情覆盖区间</span>
          <span v-if="loading" class="skel on-dark" style="min-width:190px"></span>
          <span v-else-if="error" class="hs-v">加载失败</span>
          <span v-else class="hs-v">{{ coverageText }}</span></div>
        <div class="hs-row"><span class="hs-k">B 样本复算</span>
          <span v-if="loading" class="skel on-dark" style="min-width:120px"></span>
          <span v-else class="hs-v" :class="!error && samplePassed ? 'ok' : 'warn'">{{ error ? '加载失败' : (samplePassed ? '已通过' : '未运行') }}
            <button class="btn sm ghost" :disabled="sampling || loading" @click="runSample">{{ sampling ? '复算中…' : '运行复算' }}</button></span></div>
      </div>
    </section>

    <div class="grid c3">
      <div class="stat"><div class="row1"><span class="k">真实 ETF</span><span class="icon"><v-icon name="layers" :size="18"></v-icon></span></div>
        <div class="v"><span v-if="loading" class="skel"></span><template v-else>{{ error ? '—' : (ov.realEtfCount || 0) }}<small>只</small></template></div></div>
      <div class="stat"><div class="row1"><span class="k">行情记录</span><span class="icon"><v-icon name="bars" :size="18"></v-icon></span></div>
        <div class="v"><span v-if="loading" class="skel"></span><template v-else>{{ error ? '—' : (ov.barCount || 0).toLocaleString('zh-CN') }}</template></div></div>
      <div class="stat"><div class="row1"><span class="k">回测运行</span><span class="icon"><v-icon name="play" :size="18"></v-icon></span></div>
        <div class="v"><span v-if="loading" class="skel"></span><template v-else>{{ error ? '—' : (ov.runCount || 0) }}</template></div></div>
    </div>

    <div class="card">
      <div class="card-head"><h3>最近运行</h3><a v-if="runs.length > 6" class="link" href="#/history">查看全部</a></div>
      <div v-if="runs.length" class="tbl-wrap">
        <table class="tbl"><thead><tr><th>区间</th><th>运行类型</th><th>数据类型</th><th>状态</th><th>操作</th></tr></thead>
        <tbody><tr v-for="r in runs.slice(0, 6)" :key="r.runId">
          <td class="num-l">{{ (r.startDate || '-') + ' 至 ' + (r.endDate || '-') }}</td>
          <td>{{ r.purpose === 'sample_check' ? '样本复算' : '历史回测' }}</td>
          <td>{{ r.dataType === 'synthetic' ? '虚构教学数据' : '真实历史数据' }}</td>
          <td><span :class="'badge ' + (r.status === 'SUCCESS' ? 'green' : (r.status === 'FAILED' ? 'red' : 'amber'))">{{ statusText(r.status) }}</span>
            <span v-if="r.status === 'FAILED' && r.message" class="small muted">{{ r.message }}</span></td>
          <td><a v-if="r.status === 'SUCCESS'" class="link" :href="'#/result?run_id=' + r.runId">查看结果</a></td>
        </tr></tbody></table>
      </div>
      <div v-else class="empty"><span>暂无运行记录</span>
        <button class="btn sm" :disabled="sampling" @click="runSample">{{ sampling ? '复算中…' : '运行 B 样本复算' }}</button></div>
    </div>
  </div>`,
  data() { return { ov: {}, runs: [], loading: true, error: "", sampling: false }; },
  computed: {
    coverageText() {
      const o = this.ov;
      return o.coverageStart ? o.coverageStart + " 至 " + o.coverageEnd : "未下载";
    },
    samplePassed() { return !!this.ov.sampleCheckPassed; },
    primaryLabel() { return this.ov.dataReady ? "运行回测" : "下载行情"; }
  },
  async mounted() { await this.reload(); },
  methods: {
    async reload() {
      this.loading = true;
      this.error = "";
      try {
        const res = await Promise.all([api("/api/overview"), api("/api/runs")]);
        this.ov = res[0] || {};
        this.runs = res[1] || [];
      } catch (e) {
        this.error = e.message;
      } finally {
        this.loading = false;
      }
    },
    primaryAction() {
      if (this.error) { this.reload(); return; }
      this.$root.go(this.ov.dataReady ? "backtest" : "data");
    },
    async runSample() {
      this.sampling = true;
      try {
        const res = await api("/api/backtest/sample-check", { method: "POST" });
        const pass = res.checks.filter(c => c.pass).length;
        toast("样本复算完成：" + pass + "/" + res.checks.length + " 项通过", pass === res.checks.length ? "ok" : "err");
        this.$root.goResult(res.runId);
      } catch (e) { toast(e.message, "err"); }
      finally { this.sampling = false; }
    },
    statusText(s) { return s === "SUCCESS" ? "成功" : (s === "FAILED" ? "失败" : "运行中"); },
    aiContext() {
      const o = this.ov;
      let s = "【概览页】真实ETF " + o.realEtfCount + " 只,行情 " + o.barCount + " 条,覆盖 " + o.coverageStart + "~" + o.coverageEnd
        + ",回测 " + o.runCount + " 次;样本复算" + (o.sampleCheckPassed ? "已通过" : "未通过") + "。\n最近运行:\n";
      for (const r of this.runs.slice(0, 5)) {
        s += "- " + r.runId + " " + r.purpose + " " + r.startDate + "~" + r.endDate + " " + r.status + (r.note ? "(" + r.note + ")" : "") + "\n";
      }
      return s;
    }
  }
};

/* ================= 视图:数据管理(抓取) ================= */
const DataPage = {
  mixins: [helpers],
  template: `
  <div>
    <div class="card">
      <div class="card-head">
        <h3>行情数据<span class="card-sub">东方财富 · 日频 · 前复权</span></h3>
        <div class="toolbar">
          <button class="btn sm" :disabled="busy" @click="refreshAll">{{ busy ? '抓取中…' : '更新全部' }}</button>
          <button class="btn sm ghost" @click="aiCheckData"><v-icon name="spark" :size="15"></v-icon>检查数据质量</button>
        </div>
      </div>
      <div class="toolbar add-bar">
        <div class="field"><label for="add-code">添加 ETF</label>
          <div class="with-unit">
            <input id="add-code" class="input" v-model="addCode" placeholder="6 位代码" maxlength="6" inputmode="numeric" @keydown.enter="addEtf">
            <button class="btn sm ghost" :disabled="adding" @click="addEtf">{{ adding ? '抓取中…' : '添加并抓取' }}</button>
          </div></div>
        <div class="field"><label>导入 CSV</label>
          <div class="with-unit">
            <label class="btn sm ghost">选择文件<input type="file" accept=".csv" hidden @change="importCsv($event.target)"></label>
            <details class="menu"><summary class="link-btn">格式要求</summary>
              <div class="menu-panel menu-left">UTF-8 编码，首行为表头：<span class="mono">code,date,open,high,low,close,volume,amount</span>。日期格式 YYYY-MM-DD；同一代码同一日期的记录会覆盖更新。</div>
            </details>
          </div></div>
      </div>
      <div class="fetch-log" v-if="logLines.length" aria-live="polite">
        <div v-for="(l, i) in logLines" :key="i"><span class="ts">{{ l.ts }}</span><span :class="l.ok ? 'ok' : 'fail'">{{ l.text }}</span></div>
      </div>
      <div class="tbl-wrap">
        <table class="tbl"><thead><tr>
          <th>代码</th><th>名称</th><th>覆盖区间</th><th class="num">条数</th><th>标记</th><th>操作</th>
        </tr></thead><tbody>
          <tr v-for="r in etfs" :key="r.code">
            <td><b>{{ r.code }}</b></td><td>{{ r.name || '-' }}</td>
            <td class="num-l">{{ r.coverageStart ? r.coverageStart + ' 至 ' + r.coverageEnd : '无数据' }}</td>
            <td class="num">{{ (r.barCount || 0).toLocaleString('zh-CN') }}</td>
            <td><span v-if="r.benchmark" class="badge teal">基准</span>
                <span v-if="r.defensive" class="badge amber">防御</span>
                <span v-if="!r.enabled" class="badge gray">停用</span>
                <span :class="'badge ' + srcBadge(r.source)">{{ srcLabel(r.source) }}</span></td>
            <td><button v-if="r.source !== 'DEMO'" class="link-btn" :disabled="busyCode === r.code" @click="refreshOne(r.code)">{{ busyCode === r.code ? '更新中…' : '更新' }}</button>
                <button v-if="r.barCount > 0" class="link-btn" @click="preview(r.code)">预览</button></td>
          </tr>
        </tbody></table>
      </div>
    </div>

    <div class="card" v-if="pv.bars && pv.bars.length">
      <div class="card-head"><h3>{{ pv.name }}（{{ pv.code }}）收盘价</h3><button class="link-btn" @click="pv = {}">收起</button></div>
      <div ref="pvChart"></div>
      <div class="tbl-wrap" style="margin-top:12px;max-height:300px">
        <table class="tbl"><thead><tr><th>日期</th><th class="num">开盘</th><th class="num">最高</th><th class="num">最低</th><th class="num">收盘</th><th class="num">成交量（手）</th><th class="num">成交额（元）</th></tr></thead>
        <tbody><tr v-for="b in pv.bars.slice(-10).reverse()" :key="b.date">
          <td class="num">{{ b.date }}</td><td class="num">{{ fmtNum(b.open, 3) }}</td><td class="num">{{ fmtNum(b.high, 3) }}</td>
          <td class="num">{{ fmtNum(b.low, 3) }}</td><td class="num">{{ fmtNum(b.close, 3) }}</td>
          <td class="num">{{ fmtQty(b.volume) }}</td><td class="num">{{ fmtMoney(b.amount) }}</td>
        </tr></tbody></table>
      </div>
    </div>
  </div>`,
  data() {
    return { etfs: [], addCode: "", adding: false, busy: false, busyCode: "", logLines: [], pv: {} };
  },
  async mounted() { await this.load(); },
  methods: {
    srcBadge(s) { return s === "EASTMONEY" ? "green" : s === "DEMO" ? "amber" : "gray"; },
    srcLabel(s) { return s === "EASTMONEY" ? "东财抓取" : s === "DEMO" ? "虚构" : s === "MANUAL_CSV" ? "导入" : "-"; },
    log(text, ok) {
      this.logLines.push({ ts: new Date().toLocaleTimeString("zh-CN", { hour12: false }), text, ok: ok !== false });
    },
    async load() {
      try { this.etfs = await api("/api/etfs"); } catch (e) { toast(e.message, "err"); }
    },
    async refreshOne(code) {
      this.busyCode = code;
      try {
        const res = await api("/api/data/refresh/" + code, { method: "POST" });
        this.log(res.message, true);
        toast(res.message, "ok");
        await this.load();
      } catch (e) { this.log(code + " 抓取失败：" + e.message, false); toast(code + " 失败：" + e.message, "err"); }
      finally { this.busyCode = ""; }
    },
    async refreshAll() {
      this.busy = true;
      this.log("开始抓取候选池全部 ETF 行情…", true);
      try {
        const res = await api("/api/data/refresh-all", { method: "POST" });
        for (const r of res.results || []) this.log(r.message || "", !!r.ok);
        toast(res.message, "ok");
        this.load();
      } catch (e) { this.log("抓取失败:" + e.message, false); toast(e.message, "err"); }
      finally { this.busy = false; }
    },
    async addEtf() {
      const code = (this.addCode || "").trim();
      if (!code) return;
      this.adding = true;
      this.log("添加 ETF " + code + " 并抓取历史行情…", true);
      try {
        const res = await api("/api/data/add-etf", { method: "POST", body: JSON.stringify({ code }) });
        this.log(res.message + "(" + (res.name || "") + ")", true);
        toast(res.message, "ok");
        this.addCode = "";
        this.load();
      } catch (e) { this.log("添加失败:" + e.message, false); toast(e.message, "err"); }
      finally { this.adding = false; }
    },
    aiCheckData() {
      this.$root.openAi();
      this.$root.sendAi("检查所有 ETF 的行情数据质量,指出异常、缺失和覆盖不足,并给出是否可以运行回测的结论");
    },
    aiAnalyze(q) {
      this.$root.openAi();
      this.$root.sendAi(q);
    },
    async importCsv(input) {
      const file = input.files && input.files[0];
      if (!file) return;
      const fd = new FormData();
      fd.append("file", file);
      try {
        const res = await fetch("/api/data/import-csv", { method: "POST", body: fd });
        const body = await res.json();
        if (!res.ok) throw new Error(body.message || "导入失败");
        this.log(body.message, true);
        toast(body.message, "ok");
        this.load();
      } catch (e) { toast("导入失败:" + e.message, "err"); }
      input.value = "";
    },
    async preview(code) {
      try {
        this.pv = await api("/api/etfs/" + code + "/bars?limit=120");
        this.$nextTick(() => {
          renderLineChart(this.$refs.pvChart, {
            series: [{ name: "收盘价", color: "#0f766e", points: this.pv.bars.map(b => ({ label: b.date, value: b.close })) }],
            height: 260, xLabel: d => d.slice(0, 7), base: null, yFmt: v => v.toFixed(3)
          });
        });
      } catch (e) { toast(e.message, "err"); }
    },
    aiContext() {
      let s = "【数据管理页】候选池行情覆盖:\n";
      for (const r of this.etfs.filter(e => e.source !== "DEMO")) {
        s += "- " + r.code + " " + (r.name || "") + " " + (r.coverageStart || "无") + "~" + (r.coverageEnd || "-") + " 共" + r.barCount + "条\n";
      }
      return s;
    }
  }
};

/* ================= 视图:因子库 ================= */
const FIELD_22 = [
  ["factorId", "因子编号"], ["factorName", "因子名称"], ["factorCategory", "因子类别"],
  ["scope", "适用对象与计算范围"], ["meaning", "因子含义"], ["formula", "计算公式或步骤"],
  ["requiredData", "所需数据"], ["inputFreq", "输入数据频率"], ["calcFreq", "因子计算频率"],
  ["calcParams", "计算参数"], ["minHistory", "最少历史数据要求"], ["outputDesc", "输出名称、形式与单位"],
  ["direction", "方向假设"], ["dataCaliber", "数据口径"], ["missingPolicy", "缺失值处理"],
  ["outlierPolicy", "异常值处理"], ["standardization", "标准化处理"], ["neutralization", "中性化处理"],
  ["availability", "数据可用时间与滞后要求"], ["sourceName", "定义来源(资料)"], ["sourceDetail", "定义来源(位置与核对)"],
  ["candidateSource", "候选数据来源"], ["limits", "使用限制"], ["tableVersion", "因子表版本"]
];

const FactorsPage = {
  mixins: [helpers],
  template: `
  <div>
    <div class="card" v-for="f in factors" :key="f.factorId">
      <div class="card-head">
        <h3>{{ f.factorName }}<span class="badge teal">{{ f.factorCategory }}</span><span class="badge gray mono">{{ f.factorId }}</span></h3>
        <a v-if="f.sourceUrl" class="link" :href="f.sourceUrl" target="_blank" rel="noopener">查看定义来源</a>
      </div>
      <p class="mt0">{{ f.meaning }}</p>
      <dl class="meta">
        <div><dt>公式</dt><dd class="mono">{{ f.formula }}</dd></div>
      </dl>
      <dl class="meta" style="margin-top:10px">
        <div><dt>参数</dt><dd class="mono">{{ f.calcParams || '无' }}</dd></div>
        <div><dt>定义来源</dt><dd>{{ f.sourceName }}<span v-if="f.sourceDetail" class="muted">，{{ f.sourceDetail }}</span></dd></div>
      </dl>
      <details class="more"><summary>完整定义（22 列）</summary>
        <dl class="def">
          <div v-for="[k, label] in fieldCols" :key="k"><dt>{{ label }}</dt>
            <dd>{{ f[k] === null || f[k] === undefined || f[k] === '' ? '留空' : f[k] }}</dd></div>
        </dl>
      </details>
    </div>

    <div class="card">
      <div class="card-head"><h3>因子计算</h3></div>
      <div class="form-row">
        <div class="field"><label for="calc-code">资产</label><select id="calc-code" v-model="calc.code">
          <option v-for="e in etfs.filter(e => e.barCount > 0)" :key="e.code" :value="e.code">{{ e.code }} {{ e.name }}</option>
        </select></div>
        <div class="field"><label for="calc-factor">因子</label><select id="calc-factor" v-model="calc.factor">
          <option v-for="f in factors" :key="f.factorId" :value="f.factorId">{{ f.factorName }}</option>
        </select></div>
        <div class="field"><label for="calc-window">窗口</label><div class="with-unit">
          <input id="calc-window" v-model.number="calc.window" type="number" min="2" max="500"><span class="unit">交易日</span></div></div>
        <button class="btn" @click="calcFactor">计算最近 30 期</button>
      </div>
      <div ref="calcChart"></div>
      <div class="tbl-wrap" style="margin-top:12px;max-height:340px" v-if="calcRows.length">
        <table class="tbl"><thead><tr><th>日期</th><th class="num">收盘价</th><th class="num">因子值</th></tr></thead><tbody>
          <tr v-for="r in calcRows" :key="r.date">
            <td class="num">{{ r.date }}</td><td class="num">{{ fmtNum(r.close, 4) }}</td>
            <td class="num"><b v-if="r.value !== null">{{ fmtNum(r.value, 6) }}</b><span v-else class="badge gray">NA</span></td>
          </tr>
        </tbody></table>
      </div>
    </div>
  </div>`,
  data() { return { factors: [], etfs: [], calc: { code: "", factor: "g01_mom_n", window: 20 }, calcRows: [], fieldCols: FIELD_22 }; },
  async mounted() {
    try {
      this.factors = await api("/api/factors");
      this.etfs = await api("/api/etfs");
      const first = this.etfs.find(e => e.barCount > 0);
      if (first) this.calc.code = first.code;
    } catch (e) { toast(e.message, "err"); }
  },
  methods: {
    async calcFactor() {
      if (!this.calc.code) { toast("暂无可用资产数据", "err"); return; }
      try {
        const d = await api("/api/factors/" + this.calc.factor + "/values?code=" + this.calc.code
          + "&window=" + (this.calc.window || 20) + "&limit=30");
        this.calcRows = d.rows.slice().reverse();
        this.$nextTick(() => {
          renderLineChart(this.$refs.calcChart, {
            series: [{ name: d.factorId + "(W=" + d.window + ")", color: "#0f766e",
              points: d.rows.map(r => ({ label: r.date, value: r.value })) }],
            height: 260, xLabel: d => d.slice(5), base: null, yFmt: v => v.toFixed(4)
          });
        });
      } catch (e) { toast(e.message, "err"); }
    },
    aiContext() {
      return "【因子库页】定义因子:\n" + this.factors.map(f =>
        "- " + f.factorId + " " + f.factorName + ":" + (f.meaning || "").slice(0, 80)).join("\n");
    }
  }
};

/* ================= 视图:策略配置 ================= */
const StrategyPage = {
  mixins: [helpers],
  template: `
  <div>
    <div class="split">
      <div class="card">
        <h3>参数设置</h3>
        <div class="form-row">
          <div class="field" style="flex:1 1 220px"><label for="f-name">策略名称</label><input id="f-name" v-model="f.name"></div>
          <div class="field" style="flex:1 1 220px"><label for="f-note">备注</label><input id="f-note" v-model="f.note" placeholder="可选"></div>
        </div>
        <div class="form-row">
          <div class="field"><label for="f-w">动量窗口</label>
            <div class="with-unit"><input id="f-w" v-model.number="f.momentumWindow" type="number" min="2" max="500"><span class="unit">交易日</span></div></div>
          <div class="field"><label for="f-k">持有数量</label>
            <div class="with-unit"><input id="f-k" v-model.number="f.holdingCount" type="number" min="1" max="10"><span class="unit">只</span></div></div>
          <div class="field"><label for="f-freq">调仓频率</label>
            <select id="f-freq" v-model="f.rebalanceFreq"><option value="WEEKLY">每周</option><option value="MONTHLY">每月</option></select></div>
        </div>
        <div class="form-row">
          <label class="checkline"><input type="checkbox" v-model="f.defensiveEnabled">防御开关</label>
          <div class="field"><label for="f-dth">防御阈值</label><input id="f-dth" v-model.number="f.defensiveThreshold" type="number" step="0.01"></div>
          <div class="field"><label for="f-dmode">防御模式</label>
            <select id="f-dmode" v-model="f.defensiveMode"><option value="CASH">转现金</option><option value="ASSET">转防御资产</option></select></div>
          <div class="field" v-show="f.defensiveMode === 'ASSET'"><label for="f-dasset">防御资产</label>
            <select id="f-dasset" v-model="f.defensiveAsset"><option v-for="e in defensiveEtfs" :key="e.code" :value="e.code">{{ e.code }} {{ e.name }}</option></select></div>
        </div>
        <div class="form-row">
          <label class="checkline"><input type="checkbox" v-model="f.trendFilterEnabled">趋势过滤</label>
          <div class="field"><label for="f-tw">均线窗口</label>
            <div class="with-unit"><input id="f-tw" v-model.number="f.trendWindow" type="number" min="2" max="500"><span class="unit">交易日</span></div></div>
          <div class="field"><label for="f-tth">均线比值阈值</label><input id="f-tth" v-model.number="f.trendThreshold" type="number" step="0.01"></div>
        </div>
        <div class="form-row">
          <div class="field"><label for="f-fee">手续费率</label><input id="f-fee" v-model.number="f.commissionRate" type="number" step="0.0001"></div>
          <div class="field"><label for="f-min">最低手续费</label>
            <div class="with-unit"><input id="f-min" v-model.number="f.minimumCommission" type="number" step="0.01"><span class="unit">元</span></div></div>
          <div class="field"><label for="f-slip">滑点率</label><input id="f-slip" v-model.number="f.slippageRate" type="number" step="0.0001"></div>
        </div>
        <div class="form-row">
          <div class="field"><label for="f-cash">初始资金</label>
            <div class="with-unit"><input id="f-cash" v-model.number="f.initialCash" type="number" min="1000"><span class="unit">元</span></div></div>
          <div class="field" style="flex:1 1 220px"><label for="f-bench">基准资产</label>
            <select id="f-bench" v-model="f.benchmarkAsset" style="width:100%">
              <option v-for="e in realEtfs" :key="e.code" :value="e.code">{{ e.code }} {{ e.name }}</option></select></div>
        </div>
        <div class="toolbar">
          <button class="btn" :disabled="saving" @click="save"><v-icon name="save" :size="16"></v-icon>{{ saving ? '保存中…' : '保存新版本' }}</button>
          <button class="btn ghost" @click="previewBJson">预览 B_主策略.json</button>
        </div>
      </div>

      <div class="card">
        <h3>规则预览</h3>
        <dl class="rules"><template v-for="r in rules" :key="r.k"><dt>{{ r.k }}</dt><dd>{{ r.v }}</dd></template></dl>
      </div>
    </div>

    <div class="card">
      <div class="card-head"><h3>已保存的配置</h3></div>
      <div v-if="list.length" class="tbl-wrap">
        <table class="tbl"><thead><tr>
          <th>名称</th><th class="num">W</th><th class="num">K</th><th>频率</th><th>防御</th>
          <th class="num">费率</th><th class="num">滑点</th><th>基准</th><th>操作</th>
        </tr></thead><tbody><tr v-for="c in list" :key="c.id">
          <td><b>{{ c.name }}</b> <span class="badge gray">v{{ c.version }}</span></td>
          <td class="num">{{ c.momentumWindow }}</td><td class="num">{{ c.holdingCount }}</td>
          <td>{{ c.rebalanceFreq === 'MONTHLY' ? '每月' : '每周' }}</td>
          <td>{{ c.defensiveEnabled ? (c.defensiveMode === 'ASSET' ? '转 ' + c.defensiveAsset : '转现金') : '关' }}</td>
          <td class="num">{{ fmtPct(c.commissionRate, 2) }}</td>
          <td class="num">{{ fmtPct(c.slippageRate, 2) }}</td>
          <td class="num">{{ c.benchmarkAsset }}</td>
          <td><button class="link-btn" @click="loadCfg(c.id)">载入</button>
            <button class="link-btn" @click="showBJson(c.id)">B_主策略.json</button>
            <button class="link-btn danger" @click="delCfg(c)">删除</button></td>
        </tr></tbody></table>
      </div>
      <div v-else class="empty">还没有保存的策略配置</div>
    </div>
  </div>`,
  data() {
    return {
      f: { name: "ETF动量轮动-默认方案", momentumWindow: 20, holdingCount: 2, rebalanceFreq: "WEEKLY",
        defensiveEnabled: true, defensiveThreshold: 0, defensiveMode: "CASH", defensiveAsset: "511010",
        trendFilterEnabled: false, trendWindow: 20, trendThreshold: 1,
        commissionRate: 0.001, minimumCommission: 0, slippageRate: 0.0005,
        initialCash: 100000, benchmarkAsset: "510300", note: "" },
      list: [], etfs: [], saving: false
    };
  },
  computed: {
    realEtfs() { return this.etfs.filter(e => e.enabled && e.source !== "DEMO"); },
    defensiveEtfs() { return this.etfs.filter(e => e.defensive); },
    rules() {
      const c = this.f;
      const freq = c.rebalanceFreq === "MONTHLY" ? "每月" : "每周";
      return [
        { k: "入选", v: freq + "最后一个交易日收盘后，按 " + c.momentumWindow + " 日动量降序取前 " + c.holdingCount + " 只，并列时按代码升序" },
        { k: "趋势过滤", v: c.trendFilterEnabled ? "开启，均线比值（d=" + c.trendWindow + "）低于 " + c.trendThreshold + " 才可入选" : "关闭" },
        { k: "退出", v: "落选或防御触发时，次日开盘全部卖出；重新入选可再买入；已持有且仍在目标内不调仓" },
        { k: "防御", v: c.defensiveEnabled
          ? "候选池为空或最强动量低于 " + c.defensiveThreshold + " 时，" + (c.defensiveMode === "ASSET" ? "全部转入 " + c.defensiveAsset : "全部转为现金")
          : "关闭" },
        { k: "成交", v: "T 日收盘产生信号，T+1 开盘价成交；滑点 " + fmtPct(c.slippageRate) + "，双边手续费 " + fmtPct(c.commissionRate, 4) },
        { k: "持仓", v: "每只目标权重 1/" + c.holdingCount + "，按扣费后可负担的最大整数份数买入，余款留现金" },
        { k: "基准", v: c.benchmarkAsset + " 买入持有，相同资金、区间与费用，期末不强平" }
      ];
    }
  },
  async mounted() {
    AI_BUS.strategyForm = this.f;
    try {
      this.etfs = await api("/api/etfs");
      this.list = await api("/api/strategies");
      if (this.list.length) this.loadCfg(this.list[0].id);
    } catch (e) { toast(e.message, "err"); }
  },
  unmounted() {
    if (AI_BUS.strategyForm === this.f) AI_BUS.strategyForm = null;
  },
  methods: {
    pct(v, d) { return (v * 100).toFixed(d === undefined ? 4 : d) + "%"; },
    async save() {
      this.saving = true;
      try {
        const res = await api("/api/strategies", { method: "POST", body: JSON.stringify(this.f) });
        toast("已保存为 v" + res.version, "ok");
        this.list = await api("/api/strategies");
      } catch (e) { toast("保存失败:" + e.message, "err"); }
      finally { this.saving = false; }
    },
    async loadCfg(id) {
      try {
        const c = await api("/api/strategies/" + id);
        Object.assign(this.f, c);
        toast("已载入 " + c.name + " v" + c.version, "ok");
      } catch (e) { toast(e.message, "err"); }
    },
    async delCfg(c) {
      if (!confirm("确认删除 " + c.name + "(id=" + c.id + ")?")) return;
      try {
        await api("/api/strategies/" + c.id, { method: "DELETE" });
        this.list = await api("/api/strategies");
      } catch (e) { toast(e.message, "err"); }
    },
    async showBJson(id) {
      try {
        const json = await api("/api/strategies/" + id + "/b-json");
        openModal("B_主策略.json(12 项规则)", '<pre class="code">' + esc(JSON.stringify(json, null, 2)) + "</pre>", 760);
      } catch (e) { toast(e.message, "err"); }
    },
    previewBJson() {
      const c = this.f, freq = c.rebalanceFreq === "MONTHLY" ? "每月" : "每周";
      const json = {
        strategy_name: "ETF动量轮动策略(W=" + c.momentumWindow + ",K=" + c.holdingCount + "," + freq + "调仓)",
        universe: "候选池为启用状态的ETF(见数据管理页);样本内不变",
        factor_table_version: "v1",
        factors: [{ factor_id: "g01_mom_n", parameters: { W: c.momentumWindow } }],
        entry_rule: freq + "最后一个交易日收盘后,候选池每只ETF按" + c.momentumWindow + "日动量降序排名,取前" + c.holdingCount + "只为目标",
        exit_rule: "跌出目标次日开盘全部卖出;重新入选可再买入;已持有且在目标内不调仓(缓冲)",
        position_rule: "仅多头;每只目标权重1/" + c.holdingCount + ",扣费后可负担最大整数份,余款留现金",
        rebalance: freq + "最后一个交易日收盘检查;仅目标与持仓不同时交易",
        execution: "T日15:05信号,T+1 09:30开盘价含滑点成交(+08:00);当日无行情顺延",
        costs: "双边手续费" + this.pct(c.commissionRate) + "(最低" + c.minimumCommission + "元);滑点" + this.pct(c.slippageRate) + "计入价格",
        exceptions: "窗口不足/无效价不参与排名;资金不足1份、缺价、停牌顺延;末日信号不执行",
        baseline: c.benchmarkAsset + "共同起点开盘买入持有,相同资金/区间/费用,期末收盘估值不强平"
      };
      openModal("B_主策略.json 预览(保存后可从列表导出)", '<pre class="code">' + esc(JSON.stringify(json, null, 2)) + "</pre>", 760);
    },
    aiContext() {
      const c = this.f;
      return "【策略配置页】当前表单:W=" + c.momentumWindow + " K=" + c.holdingCount + " "
        + (c.rebalanceFreq === "MONTHLY" ? "月调仓" : "周调仓") + " 防御" + (c.defensiveEnabled ? "开(阈值" + c.defensiveThreshold + ")"
        : "关") + " 费率" + c.commissionRate + " 滑点" + c.slippageRate + " 初始资金" + c.initialCash + " 基准" + c.benchmarkAsset;
    }
  }
};

/* ================= 视图:回测运行 ================= */
const BacktestPage = {
  mixins: [helpers],
  template: `
  <div>
    <div class="card">
      <div class="card-head"><h3>历史回测<span class="card-sub">真实历史数据</span></h3>
        <span v-if="coverage" class="card-sub">行情截至 {{ coverage }}</span></div>
      <div class="callout warn" v-if="dataWarn" v-html="dataWarn"></div>
      <div class="callout warn" v-if="ready && !strategies.length">还没有策略配置，请先到 <a href="#/strategy">策略配置</a> 保存。</div>
      <div class="form-row">
        <div class="field"><label for="r-cfg">策略配置</label>
          <select id="r-cfg" v-model="r.configId" style="min-width:260px">
            <option v-for="s in strategies" :key="s.id" :value="s.id">{{ s.name }} v{{ s.version }}</option>
          </select></div>
        <div class="field"><label for="r-start">开始日期</label><input id="r-start" v-model="r.start" type="date"></div>
        <div class="field"><label for="r-end">结束日期</label><input id="r-end" v-model="r.end" type="date"></div>
      </div>
      <div class="form-row">
        <label class="checkline"><input type="checkbox" v-model="r.zeroCost">零成本对照（费率与滑点为 0）</label>
        <div class="field"><label for="r-w">覆盖动量窗口</label>
          <div class="with-unit"><input id="r-w" v-model.number="r.overrideMomentumWindow" type="number" placeholder="不改" min="2" max="500"><span class="unit">交易日</span></div></div>
        <div class="field"><label for="r-k">覆盖持有数量</label>
          <div class="with-unit"><input id="r-k" v-model.number="r.overrideHoldingCount" type="number" placeholder="不改" min="1" max="10"><span class="unit">只</span></div></div>
      </div>
      <div class="form-row"><div class="field" style="flex:1 1 100%"><label for="r-note">备注</label>
        <input id="r-note" v-model="r.note" placeholder="可选"></div></div>
      <div class="toolbar">
        <button class="btn" :disabled="running || !r.configId" @click="run"><v-icon name="play" :size="16"></v-icon>{{ running ? '回测运行中…' : '运行回测' }}</button>
      </div>
      <div :class="'callout mt ' + statusCls" v-if="statusMsg" v-html="statusMsg"></div>
    </div>

    <div class="card">
      <div class="card-head"><h3>B 样本复算<span class="card-sub">虚构教学数据 · 核对引擎与手工演算</span></h3>
        <button class="btn sm" :disabled="sampling" @click="runSample"><v-icon name="check" :size="15"></v-icon>{{ sampling ? '复算中…' : '运行样本复算' }}</button></div>
      <div v-if="checks" :class="'callout ' + (allPass ? 'ok' : 'err')">
        <b>{{ allPass ? '全部通过' : '存在差异' }}</b>（{{ passCount }}/{{ checks.length }} 项）
        <a :href="'#/result?run_id=' + sampleRunId">查看结果</a></div>
      <div class="tbl-wrap" v-if="checks">
        <table class="tbl"><thead><tr><th>检查项</th><th class="num">预期值</th><th class="num">实际值</th><th class="num">容差</th><th>结果</th><th>说明</th></tr></thead>
        <tbody><tr v-for="c in checks" :key="c.name">
          <td class="wrap">{{ c.name }}</td>
          <td class="num">{{ c.tolerance >= 0.5 ? c.expected : (+c.expected).toFixed(6) }}</td>
          <td class="num"><b>{{ c.actual === null ? 'NA' : (c.tolerance >= 0.5 ? c.actual : (+c.actual).toFixed(6)) }}</b></td>
          <td class="num">{{ c.tolerance >= 0.5 ? '整数' : c.tolerance }}</td>
          <td><span :class="'badge ' + (c.pass ? 'green' : 'red')">{{ c.pass ? '通过' : '未通过' }}</span></td>
          <td class="wrap small muted">{{ c.hint }}</td>
        </tr></tbody></table>
      </div>
      <div v-else class="empty">还没有运行过复算</div>
    </div>
  </div>`,
  data() {
    return { strategies: [], r: { configId: null, start: "2019-01-01", end: "", zeroCost: false,
      overrideMomentumWindow: null, overrideHoldingCount: null, note: "" },
      running: false, statusMsg: "", statusCls: "",
      sampling: false, checks: null, sampleRunId: "", dataWarn: "", coverage: "", ready: false };
  },
  computed: {
    allPass() { return this.checks && this.checks.every(c => c.pass); },
    passCount() { return this.checks ? this.checks.filter(c => c.pass).length : 0; }
  },
  async mounted() {
    AI_BUS.backtestForm = this.r;
    try {
      const ov = await api("/api/overview");
      this.r.end = ov.coverageEnd || new Date().toISOString().slice(0, 10);
      this.coverage = ov.coverageEnd || "";
      if (!ov.dataReady) this.dataWarn = "<b>缺少行情数据</b>：请先到 <a href='#/data'>数据管理</a> 抓取行情";
      this.strategies = await api("/api/strategies");
      if (this.strategies.length) this.r.configId = this.strategies[0].id;
      this.ready = true;
    } catch (e) { toast(e.message, "err"); }
  },
  unmounted() {
    if (AI_BUS.backtestForm === this.r) AI_BUS.backtestForm = null;
  },
  methods: {
    async run() {
      if (!this.r.configId) { toast("请先在策略配置页保存一个策略", "err"); return; }
      this.running = true;
      this.statusCls = "";
      this.statusMsg = "正在计算:" + this.r.start + " ~ " + this.r.end + "(信号、逐日估值与指标统计)";
      try {
        const body = Object.assign({}, this.r, { purpose: "historical_backtest" });
        const res = await api("/api/backtest/run", { method: "POST", body: JSON.stringify(body) });
        if (res.status === "SUCCESS") {
          this.statusCls = "ok";
          this.statusMsg = "<b>运行成功 ✓</b> " + esc(res.message) + "<br>run_id:<span class='mono'>" + res.runId + "</span>,正在跳转结果页…";
          setTimeout(() => this.$root.goResult(res.runId), 1100);
        } else {
          this.statusCls = "err";
          this.statusMsg = "<b>运行失败</b> —— " + esc(res.message) + "<br>run_id:<span class='mono'>" + res.runId + "</span>(已记入运行历史)";
        }
      } catch (e) { this.statusCls = "err"; this.statusMsg = "<b>请求被拒绝</b> —— " + esc(e.message); }
      finally { this.running = false; }
    },
    async runSample() {
      this.sampling = true;
      try {
        const res = await api("/api/backtest/sample-check", { method: "POST" });
        this.checks = res.checks;
        this.sampleRunId = res.runId;
        toast(this.allPass ? "样本复算全部通过" : "样本复算存在差异", this.allPass ? "ok" : "err");
      } catch (e) { toast("样本复算失败:" + e.message, "err"); }
      finally { this.sampling = false; }
    },
    aiContext() {
      return "【回测运行页】准备以配置#" + this.r.configId + " 运行 " + this.r.start + "~" + this.r.end
        + (this.r.zeroCost ? "(零成本对照)" : "") + ";样本复算" + (this.checks ? (this.allPass ? "已通过" : "有差异") : "未运行");
    }
  }
};

/* ================= 视图:结果详情 ================= */
const ResultPage = {
  mixins: [helpers],
  template: `
  <div>
    <teleport to="#topbar-extra">
      <template v-if="run">
        <span :class="'badge ' + (run.purpose === 'sample_check' ? 'amber' : 'teal')">{{ run.purpose === 'sample_check' ? '样本复算' : '历史回测' }}</span>
        <span :class="'badge ' + (run.dataType === 'synthetic' ? 'amber' : 'gray')">{{ run.dataType === 'synthetic' ? '虚构教学数据' : '真实历史数据' }}</span>
        <span :class="'badge ' + statusBadge">{{ statusText }}</span>
        <a class="btn sm" :href="'/api/runs/' + run.runId + '/export-all'"><v-icon name="download" :size="15"></v-icon>导出 ZIP</a>
        <details class="menu"><summary class="btn sm ghost">更多<v-icon name="chevron" :size="14"></v-icon></summary>
          <div class="menu-list">
            <a v-for="f in exportFiles" :key="f" :href="'/api/runs/' + run.runId + '/export/' + f" @click="closeMenus()">{{ f }}</a>
            <button v-if="canRerun" @click="rerunZero">零成本重跑</button>
          </div>
        </details>
      </template>
    </teleport>

    <div v-if="loading" class="grid c4">
      <div v-for="i in 4" :key="i" class="stat"><span class="skel"></span><div class="v"><span class="skel" style="min-width:130px"></span></div></div>
    </div>

    <div v-else-if="error" class="card">
      <div class="callout err" style="margin:0">{{ error }}</div>
      <div class="toolbar mt"><a class="btn sm ghost" href="#/history">返回运行历史</a><button class="btn sm" @click="reload">重试</button></div>
    </div>

    <template v-else-if="run">
      <div class="run-bar">
        <div class="run-sum">
          <span>{{ run.strategyCode }} v{{ run.strategyVersion }}</span><span class="sep"></span>
          <span>{{ run.startDate }} 至 {{ run.endDate }}</span><span class="sep"></span>
          <span>基准 {{ benchLabel }}</span>
        </div>
        <button class="link-btn" :aria-expanded="cfgOpen ? 'true' : 'false'" @click="cfgOpen = !cfgOpen">运行配置</button>
      </div>
      <div class="cfg-panel" v-if="cfgOpen">
        <dl class="meta">
          <div><dt>run_id</dt><dd class="mono">{{ run.runId }}</dd></div>
          <div><dt>策略参数</dt><dd>{{ stratDesc }}</dd></div>
          <div><dt>初始资金</dt><dd>{{ fmtMoney(run.initialCash) }} 元</dd></div>
          <div><dt>成本假设</dt><dd>双边手续费 {{ fmtPct(costs.commission_rate, 4) }}（最低 {{ costs.minimum_commission || 0 }} 元），滑点 {{ fmtPct(costs.slippage_rate, 4) }} 计入成交价</dd></div>
          <div><dt>执行口径</dt><dd>信号在 T 日 15:05 收盘数据可用后产生，T+1 日 09:30 按开盘价成交；评价区间最后一个信号日若没有下一交易日，则不执行。</dd></div>
          <div v-if="run.note"><dt>备注</dt><dd>{{ run.note }}</dd></div>
        </dl>
      </div>

      <div v-if="run.status !== 'SUCCESS'" class="callout err">本次运行{{ statusText }}<span v-if="run.message">：{{ run.message }}</span></div>

      <div class="grid c4">
        <div class="stat"><div class="row1"><span class="k">策略累计收益</span><span class="icon"><v-icon name="up" :size="18"></v-icon></span></div>
          <div class="v" :class="signedClass(mval('total_return', 'strategy'))">{{ fmtPct(mval('total_return', 'strategy'), 2) }}</div></div>
        <div class="stat"><div class="row1"><span class="k">基准累计收益</span><span class="icon"><v-icon name="bars" :size="18"></v-icon></span></div>
          <div class="v" :class="signedClass(mval('total_return', 'benchmark'))">{{ fmtPct(mval('total_return', 'benchmark'), 2) }}</div></div>
        <div class="stat"><div class="row1"><span class="k">策略最大回撤</span><span class="icon"><v-icon name="down" :size="18"></v-icon></span></div>
          <div class="v neg">{{ fmtPct(mval('max_drawdown', 'strategy'), 2) }}</div></div>
        <div class="stat"><div class="row1"><span class="k">策略总手续费</span><span class="icon"><v-icon name="coins" :size="18"></v-icon></span></div>
          <div class="v v-sm">{{ fmtMoney(mval('total_cost', 'strategy')) }}</div></div>
      </div>

      <section class="board" aria-label="净值画板">
        <div class="board-head">
          <h3>策略与基准净值</h3>
          <div class="legend"><span class="lg"><i class="line"></i>策略</span><span class="lg"><i class="line dashed"></i>基准</span></div>
          <span class="grow"></span>
          <details class="menu"><summary class="link-btn">净值口径</summary>
            <div class="menu-panel">净值 = 账户权益 ÷ 评价起点交易前的账户权益。初始净值 1 计入历史峰值与回撤，首日损益与交易费用已计入。</div>
          </details>
          <details class="menu"><summary class="btn sm ghost"><v-icon name="spark" :size="15"></v-icon>AI 解读</summary>
            <div class="menu-list">
              <button @click="aiAsk('解释本次回测的表现与风险')">解释本次回测</button>
              <button @click="aiAsk('分析本次回测最大回撤发生在什么区间,原因是什么')">最大回撤分析</button>
              <button @click="aiAsk('分析手续费对本次回测收益的影响')">手续费影响</button>
              <button @click="aiAsk('为本次回测生成完整研究报告')">生成研究报告</button>
            </div>
          </details>
        </div>
        <div ref="navChart" class="board-body"></div>
        <div class="board-foot">移动指针或用左右方向键查看某日数值</div>
      </section>

      <div class="card">
        <h3>指标对照</h3>
        <div class="tbl-wrap">
          <table class="tbl"><thead><tr><th>指标</th><th class="num">策略</th><th class="num">基准</th><th class="num">差值（策略 − 基准）</th><th>单位</th></tr></thead>
          <tbody><tr v-for="row in metricRows" :key="row.key">
            <td><span class="def-tip" :title="row.def">{{ row.label }}</span></td>
            <td class="num">{{ row.s === null ? 'NA' : row.fmt(row.s) }}</td>
            <td class="num">{{ row.b === null ? 'NA' : row.fmt(row.b) }}</td>
            <td class="num" v-html="row.diff"></td>
            <td class="muted">{{ row.unit }}</td>
          </tr></tbody></table>
        </div>
      </div>

      <div class="card">
        <div class="tabs" role="tablist" aria-label="回测明细" ref="tabs"
             @keydown.right.prevent="nextTab(1)" @keydown.left.prevent="nextTab(-1)">
          <button v-for="t in tabList" :key="t.key" role="tab" :aria-selected="tab === t.key ? 'true' : 'false'"
                  :tabindex="tab === t.key ? 0 : -1" :class="{ active: tab === t.key }" @click="tab = t.key">{{ t.label }}</button>
        </div>

        <div v-show="tab === 'trades'" role="tabpanel" aria-label="交易记录">
          <div v-if="strategyTrades.length" class="tbl-wrap">
            <table class="tbl dense"><thead><tr>
              <th>信号时间</th><th>成交时间</th><th>资产</th><th>方向</th><th class="num">数量</th><th class="num">成交价</th>
              <th class="num">手续费</th><th class="num">滑点成本</th><th>状态</th><th>原因</th><th>操作</th>
            </tr></thead><tbody><tr v-for="(t, i) in strategyTrades" :key="t.id || i">
              <td class="num">{{ (t.signalTime || '').replace('T', ' ').slice(0, 16) }}</td>
              <td class="num">{{ (t.executionTime || 'NA').replace('T', ' ').slice(0, 16) }}</td>
              <td><b>{{ t.assetId }}</b></td>
              <td><span :class="'badge ' + (t.side === 'buy' ? 'green' : 'red')">{{ t.side === 'buy' ? '买入' : '卖出' }}</span></td>
              <td class="num">{{ fmtQty(t.quantity) }}</td>
              <td class="num">{{ fmtNum(t.price, 4) }}</td>
              <td class="num">{{ fmtMoney(t.fee) }}</td>
              <td class="num">{{ fmtMoney(t.slippageCost) }}</td>
              <td><span :class="'badge ' + (t.status === 'FILLED' ? 'green' : 'gray')">{{ t.status === 'FILLED' ? '成交' : '未成交' }}</span></td>
              <td class="wrap small">{{ t.reason }}</td>
              <td><button v-if="t.status === 'FILLED'" class="link-btn" @click="explainTrade(i)">查看解释</button></td>
            </tr></tbody></table>
          </div>
          <div v-else class="empty">策略没有成交记录</div>
        </div>

        <div v-show="tab === 'decisions'" role="tabpanel" aria-label="信号决策">
          <div v-if="decisions.length" class="tbl-wrap">
            <table class="tbl"><thead><tr><th>信号日</th><th>防御触发</th><th>防御原因</th><th>操作</th></tr></thead>
            <tbody><tr v-for="d in decisions" :key="d.id || d.signalDate">
              <td class="num">{{ d.signalDate }}</td>
              <td><span :class="'badge ' + (d.defensiveTriggered ? 'amber' : 'gray')">{{ d.defensiveTriggered ? '是' : '否' }}</span></td>
              <td class="wrap small">{{ d.defensiveReason || '-' }}</td>
              <td><button class="link-btn" @click="explainDate(d.signalDate)">查看解释</button></td>
            </tr></tbody></table>
          </div>
          <div v-else class="empty">没有信号决策记录</div>
        </div>

        <div v-show="tab === 'positions'" role="tabpanel" aria-label="持仓快照">
          <div class="form-row"><div class="field"><label for="pos-date">持仓日期</label>
            <select id="pos-date" v-model="posDate" style="min-width:180px" @change="loadPositions">
              <option v-for="d in posDates.slice().reverse()" :key="d" :value="d">{{ d }}</option>
            </select></div></div>
          <div v-if="posRows.length" class="tbl-wrap">
            <table class="tbl"><thead><tr><th>序列</th><th>资产</th><th class="num">数量</th><th class="num">权重</th></tr></thead>
            <tbody><tr v-for="p in posRows" :key="p.seriesId + p.assetId">
              <td><span :class="'badge ' + (p.seriesId === 'strategy' ? 'teal' : 'gray')">{{ p.seriesId === 'strategy' ? '策略' : '基准' }}</span></td>
              <td><b>{{ p.assetId }}</b></td><td class="num">{{ fmtQty(p.quantity) }}</td><td class="num">{{ fmtPct(p.weight, 2) }}</td>
            </tr></tbody></table>
          </div>
          <div v-else class="empty">该日没有持仓（空仓或现金）</div>
        </div>

        <div v-show="tab === 'equity'" role="tabpanel" aria-label="净值明细">
          <div v-if="equityRecent.length" class="tbl-wrap" style="max-height:420px">
            <table class="tbl"><thead><tr><th>日期</th><th class="num">策略净值</th><th class="num">基准净值</th><th class="num">策略现金</th><th class="num">持仓市值</th></tr></thead>
            <tbody><tr v-for="e in equityRecent" :key="e.date">
              <td class="num">{{ e.date }}</td><td class="num">{{ fmtNum(e.navS, 6) }}</td><td class="num">{{ fmtNum(e.navB, 6) }}</td>
              <td class="num">{{ fmtMoney(e.cash) }}</td><td class="num">{{ fmtMoney(e.pv) }}</td>
            </tr></tbody></table>
          </div>
          <div v-else class="empty">暂无净值数据</div>
        </div>
      </div>
    </template>
  </div>`,
  data() {
    return { runId: "", run: null, config: {}, costs: {}, metrics: [], equity: [], trades: [], decisions: [],
      loading: true, error: "", cfgOpen: false,
      tab: "trades", tabList: [{ key: "trades", label: "交易记录" }, { key: "decisions", label: "信号决策" },
        { key: "positions", label: "持仓快照" }, { key: "equity", label: "净值明细" }],
      exportFiles: ["run.json", "config.json", "metrics.csv", "equity.csv", "positions.csv", "trades.csv", "decisions.csv"],
      posDates: [], posDate: "", posRows: [], equityRecent: [] };
  },
  computed: {
    statusText() { return statusText(this.run && this.run.status); },
    statusBadge() {
      const s = this.run && this.run.status;
      return s === "SUCCESS" ? "green" : (s === "FAILED" ? "red" : "amber");
    },
    canRerun() { return !!this.run && this.run.purpose !== "sample_check"; },
    benchLabel() { return this.run ? String(this.run.benchmark || "").replace("buy_and_hold(", "买入持有（").replace(/\)$/, "）") : ""; },
    chartHeight() { return window.innerWidth < 768 ? 260 : 340; },
    stratDesc() {
      const s = (this.config && this.config.strategy) || {};
      if (s.momentum_window === undefined) return "均线比值持有策略（" + ((this.config.strategy || {}).threshold !== undefined ? "阈值 " + this.config.strategy.threshold : "") + "）";
      return "W=" + s.momentum_window + "，K=" + s.holding_count + "，"
        + (s.rebalance_freq === "MONTHLY" ? "每月调仓" : "每周调仓")
        + (s.defensive && s.defensive.enabled ? "，防御开（" + (s.defensive.mode === "ASSET" ? "转 " + (s.defensive.asset || "") : "转现金")
          + "，阈值 " + s.defensive.threshold + "）" : "，防御关");
    },
    strategyTrades() { return this.trades.filter(t => t.seriesId === "strategy"); },
    metricRows() {
      const byKey = {};
      for (const m of this.metrics) {
        byKey[m.metric] = byKey[m.metric] || {};
        byKey[m.metric][m.seriesId] = m;
      }
      const order = ["total_return", "annual_return", "annual_vol", "max_drawdown", "sharpe", "turnover", "trade_count", "total_cost"];
      const rows = [];
      for (const k of order) {
        if (!byKey[k]) continue;
        const s = byKey[k].strategy, b = byKey[k].benchmark;
        const sv = s ? s.metricValue : null, bv = b ? b.metricValue : null;
        let diff = "NA";
        if (sv !== null && sv !== undefined && bv !== null && bv !== undefined) {
          const d = sv - bv;
          if (["total_return", "annual_return", "max_drawdown"].includes(k)) {
            const cls = k === "max_drawdown" ? (d > 0 ? "pos" : "neg") : signedClass(d);
            diff = '<span class="' + cls + '">' + (d > 0 ? "+" : "") + fmtPct(d, 2) + "</span>";
          } else diff = fmtNum(d, 3);
        }
        rows.push({ key: k, label: METRIC_LABELS[k] || k, s: sv, b: bv,
          fmt: METRIC_FMT[k] || (v => fmtNum(v)), diff, unit: (s || b).unit, def: (s || b).definition });
      }
      return rows;
    }
  },
  async mounted() {
    this.runId = this.$root.route.params.run_id || "";
    await this.reload();
  },
  methods: {
    async reload() {
      this.loading = true;
      this.error = "";
      this.run = null;
      if (!this.runId) { this.loading = false; this.error = "缺少 run_id，无法确定要查看的运行。"; return; }
      try {
        const id = this.runId;
        const detail = await api("/api/runs/" + id);
        this.run = detail.run;
        this.config = detail.config || {};
        this.costs = (detail.config && detail.config.costs) || {};
        const res = await Promise.all([
          api("/api/runs/" + id + "/metrics"), api("/api/runs/" + id + "/equity"),
          api("/api/runs/" + id + "/trades"), api("/api/runs/" + id + "/decisions"),
          api("/api/runs/" + id + "/positions")
        ]);
        this.metrics = res[0];
        this.equity = res[1];
        this.trades = res[2];
        this.decisions = res[3];
        this.posDates = res[4].dates || [];
        this.posDate = this.posDates.length ? this.posDates[this.posDates.length - 1] : "";
        this.posRows = res[4].rows || [];
        const strategy = this.equity.filter(e => e.seriesId === "strategy");
        const bench = this.equity.filter(e => e.seriesId === "benchmark");
        this.equityRecent = strategy.slice(-30).reverse().map(e => {
          const b = this.equity.find(x => x.seriesId === "benchmark" && x.tradeDate === e.tradeDate);
          return { date: e.tradeDate, navS: e.nav, navB: b ? b.nav : null, cash: e.cash, pv: e.positionValue };
        });
        this.loading = false;
        await this.$nextTick();
        const short = strategy.length <= 40;   // 短区间按"月-日"标注,长区间按"年-月"
        if (this.$refs.navChart) renderLineChart(this.$refs.navChart, {
          series: [
            { name: "策略", color: "#0F766E", points: strategy.map(e => ({ label: e.tradeDate, value: e.nav })) },
            { name: "基准", color: "#475569", dash: true, points: bench.map(e => ({ label: e.tradeDate, value: e.nav })) }
          ],
          height: this.chartHeight, legend: false,
          xLabel: short ? (d => d.slice(5)) : (d => d.slice(0, 7)), yFmt: v => v.toFixed(3)
        });
      } catch (e) {
        this.error = e.message;
      } finally {
        this.loading = false;
      }
    },
    mval(key, series) {
      const m = this.metrics.find(x => x.metric === key && x.seriesId === series);
      return m ? m.metricValue : null;
    },
    nextTab(step) {
      const keys = this.tabList.map(t => t.key);
      this.tab = keys[(keys.indexOf(this.tab) + step + keys.length) % keys.length];
      this.$nextTick(() => {
        const el = this.$refs.tabs && this.$refs.tabs.querySelector('[aria-selected="true"]');
        if (el) el.focus();
      });
    },
    async loadPositions() {
      if (!this.posDate) return;
      try {
        const d = await api("/api/runs/" + this.run.runId + "/positions?date=" + this.posDate);
        this.posRows = d.rows || [];
      } catch (e) { toast(e.message, "err"); }
    },
    explainTrade(i) {
      const t = this.strategyTrades[i];
      if (t) this.explainDate((t.signalTime || "").slice(0, 10), t);
    },
    // 交易解释:信号时间与触发条件 → 因子排名与目标权重 → 次日实际执行
    async explainDate(date, trade) {
      const show = (v, f) => (v === null || v === undefined) ? "NA" : f(v);
      try {
        const d = await api("/api/runs/" + this.run.runId + "/explain?date=" + date);
        const dec = d.decision || {};
        let html = "<h4>信号时间与触发条件</h4><p class='mt0'>信号 " + esc(date) + " 15:05";
        if (dec.execution_date) html += "，成交 " + esc(dec.execution_date) + " 09:30";
        html += (dec.momentum_window !== undefined ? "，动量窗口 " + dec.momentum_window + " 日" : "") + "。</p>";
        if (dec.factor_value !== undefined) {
          const state = dec.target_state === "hold" ? "持有" : (dec.target_state === "cash" ? "现金" : "不产生新目标");
          html += "<p>均线比值 <b class='num'>" + show(dec.factor_value, v => fmtNum(v, 6)) + "</b>，目标状态：" + state
            + (dec.note ? "。" + esc(dec.note) : "") + "</p>";
        }
        if (trade) {
          html += "<div class='callout'>" + (trade.side === "buy" ? "买入" : "卖出") + " <b>" + esc(trade.assetId)
            + "</b> " + fmtQty(trade.quantity) + " 份，成交价 " + show(trade.price, v => fmtNum(v, 4))
            + "，手续费 " + show(trade.fee, v => fmtMoney(v)) + " 元<br>" + esc(trade.reason) + "</div>";
        }
        if (dec.defensive && dec.defensive.triggered) {
          html += "<div class='callout warn'>防御触发：" + esc(dec.defensive.reason || "") + "</div>";
        }
        // 按动量排名展示;未启用趋势过滤时不显示均线比值列(此时全为 NA)
        const assets = (dec.assets || []).slice().sort((x, y) => (x.rank || 999) - (y.rank || 999));
        if (assets.length) {
          const weight = {};
          (dec.targets || []).forEach(t => { weight[t.code] = t.weight; });
          const ma = !!dec.trend_filter;
          html += "<h4>因子排名与目标权重</h4><div class='tbl-wrap' style='max-height:280px'><table class='tbl'><thead><tr><th>资产</th>"
            + "<th class='num'>动量</th>" + (ma ? "<th class='num'>均线比值</th>" : "")
            + "<th>可入选</th><th class='num'>排名</th><th class='num'>目标权重</th></tr></thead><tbody>";
          for (const a of assets) {
            const w = weight[a.code];
            html += "<tr><td><b>" + esc(a.code) + "</b>" + (w !== undefined ? " <span class='badge teal'>目标</span>" : "") + "</td>"
              + "<td class='num'>" + show(a.momentum, v => fmtPct(v, 2)) + "</td>"
              + (ma ? "<td class='num'>" + show(a.ma_ratio, v => fmtNum(v, 4)) + "</td>" : "")
              + "<td>" + (a.eligible ? "是" : "否") + "</td>"
              + "<td class='num'>" + (a.rank || "-") + "</td>"
              + "<td class='num'>" + (w === undefined ? "0%" : fmtPct(w, 0)) + "</td></tr>";
          }
          html += "</tbody></table></div>";
        }
        const ex = d.trades || [];
        html += "<h4>次日实际执行</h4>";
        if (!ex.length) {
          html += "<p class='muted'>没有产生成交。</p>";
        } else {
          html += "<div class='tbl-wrap'><table class='tbl'><thead><tr><th>资产</th><th>方向</th><th class='num'>数量</th>"
            + "<th class='num'>成交价</th><th class='num'>手续费</th><th>状态</th><th>说明</th></tr></thead><tbody>";
          for (const t of ex) {
            html += "<tr><td><b>" + esc(t.assetId) + "</b></td><td>" + (t.side === "buy" ? "买入" : "卖出") + "</td>"
              + "<td class='num'>" + fmtQty(t.quantity) + "</td>"
              + "<td class='num'>" + show(t.price, v => fmtNum(v, 4)) + "</td>"
              + "<td class='num'>" + show(t.fee, v => fmtMoney(v)) + "</td>"
              + "<td>" + (t.status === "FILLED" ? "成交" : "未成交") + "</td><td class='wrap small'>" + esc(t.reason) + "</td></tr>";
          }
          html += "</tbody></table></div>";
        }
        openModal("交易解释 · " + esc(date), html, 820);
      } catch (e) { toast(e.message, "err"); }
    },
    rerunZero() {
      closeMenus();
      if (!confirm("以相同区间和参数、零费率零滑点重新运行（生成新的 run_id），用于成本影响分析。继续？")) return;
      const body = { configId: this.run.strategyConfigId, start: this.run.startDate, end: this.run.endDate,
        purpose: "historical_backtest", zeroCost: true, note: "附加对照：零费率零滑点（对照 " + this.run.runId + "）" };
      api("/api/backtest/run", { method: "POST", body: JSON.stringify(body) })
        .then(res => {
          if (res.status === "SUCCESS") { toast("零成本对照运行成功", "ok"); this.$root.goResult(res.runId); }
          else toast("重跑失败：" + res.message, "err");
        })
        .catch(e => toast(e.message, "err"));
    },
    aiAsk(q) { closeMenus(); this.$root.openAi(); this.$root.sendAi(q); },
    aiContext() {
      if (!this.run) return "【结果页】数据加载中";
      let s = "【回测结果页】run_id=" + this.run.runId + " " + this.run.startDate + "~" + this.run.endDate
        + " 策略" + this.run.strategyCode + " v" + this.run.strategyVersion + " 基准" + this.run.benchmark + "\n指标:\n";
      for (const row of this.metricRows) {
        s += "- " + row.label + ":策略 " + (row.s === null ? "NA" : (METRIC_FMT[row.key] ? METRIC_FMT[row.key](row.s) : row.s))
          + " / 基准 " + (row.b === null ? "NA" : (METRIC_FMT[row.key] ? METRIC_FMT[row.key](row.b) : row.b)) + "\n";
      }
      const st = this.strategyTrades;
      if (st.length) {
        s += "共 " + st.length + " 笔成交,最近 3 笔:\n";
        for (const t of st.slice(-3)) s += "- " + (t.executionTime || "").slice(0, 10) + " " + t.side + " " + t.assetId + " " + fmtQty(t.quantity) + "份@" + fmtNum(t.price, 4) + "\n";
      }
      return s;
    }
  }
};

/* ================= 视图:运行历史 ================= */
const HistoryPage = {
  mixins: [helpers],
  template: `
  <div>
    <div class="card">
      <div class="card-head"><h3>运行记录</h3>
        <button class="btn sm" :disabled="loading" @click="compare">对比所选</button></div>
      <div v-if="error" class="callout err">{{ error }} <button class="link-btn" @click="load">重试</button></div>
      <div v-if="runs.length" class="tbl-wrap">
        <table class="tbl dense"><thead><tr>
          <th>对比</th><th>run_id</th><th>区间</th><th>类型</th><th>状态</th><th>备注</th><th>创建时间</th><th>操作</th>
        </tr></thead><tbody>
          <tr v-for="r in runs" :key="r.runId" :class="{ picked: isPicked(r.runId) }">
            <td><input type="checkbox" class="cmp" :value="r.runId" :aria-label="'选择 ' + r.runId + ' 参与对比'"></td>
            <td class="mono"><a :href="'#/result?run_id=' + r.runId">{{ r.runId }}</a>
              <span v-if="cmp.show && cmp.a === r.runId" class="badge navy">A</span>
              <span v-if="cmp.show && cmp.b === r.runId" class="badge navy">B</span></td>
            <td class="num-l">{{ (r.startDate || '-') + ' 至 ' + (r.endDate || '-') }}</td>
            <td>{{ r.purpose === 'sample_check' ? '样本复算' : '历史回测' }}<span class="muted small gap">{{ r.dataType === 'synthetic' ? '虚构数据' : '真实数据' }}</span></td>
            <td><span :class="'badge ' + (r.status === 'SUCCESS' ? 'green' : (r.status === 'FAILED' ? 'red' : 'amber'))">{{ statusText(r.status) }}</span></td>
            <td class="ellipsis" :title="r.note">{{ r.note || '-' }}</td>
            <td class="num-l muted">{{ (r.createdAt || '').replace('T', ' ').slice(0, 16) }}</td>
            <td><a class="link-btn" :href="'#/result?run_id=' + r.runId">查看</a>
              <a class="link-btn" :href="'/api/runs/' + r.runId + '/export-all'">ZIP</a>
              <button class="link-btn danger" @click="delRun(r)">删除</button></td>
          </tr>
        </tbody></table>
      </div>
      <div v-else-if="!loading && !error" class="empty">暂无运行记录</div>
    </div>

    <div class="card" v-if="cmp.show">
      <div class="card-head"><h3>运行对比</h3>
        <div class="legend"><span class="lg"><i class="line"></i>A {{ cmp.a }}</span><span class="lg"><i class="line dashed"></i>B {{ cmp.b }}</span></div></div>
      <div ref="cmpChart" class="board-body"></div>
      <div class="tbl-wrap" style="margin-top:16px">
        <table class="tbl"><thead><tr><th>指标（策略）</th><th class="num">A</th><th class="num">B</th><th class="num">差值（A − B）</th></tr></thead>
        <tbody><tr v-for="row in cmpRows" :key="row.key">
          <td>{{ row.label }}</td>
          <td class="num">{{ row.a === null || row.a === undefined ? 'NA' : row.fmt(row.a) }}</td>
          <td class="num">{{ row.b === null || row.b === undefined ? 'NA' : row.fmt(row.b) }}</td>
          <td class="num" v-html="row.diff"></td>
        </tr></tbody></table>
      </div>
    </div>
  </div>`,
  data() { return { runs: [], loading: true, error: "", cmp: { show: false, a: "", b: "" }, cmpRows: [] }; },
  computed: {
    chartHeight() { return window.innerWidth < 768 ? 260 : 320; }
  },
  async mounted() { await this.load(); },
  methods: {
    statusText,
    isPicked(id) { return this.cmp.show && (this.cmp.a === id || this.cmp.b === id); },
    async load() {
      this.loading = true;
      this.error = "";
      try { this.runs = await api("/api/runs"); }
      catch (e) { this.error = "运行记录加载失败：" + e.message; }
      finally { this.loading = false; }
    },
    async delRun(r) {
      if (!confirm("确认删除运行 " + r.runId + "？数据库记录与导出文件会一并删除。")) return;
      try {
        await api("/api/runs/" + r.runId, { method: "DELETE" });
        toast("已删除 " + r.runId, "ok");
        if (this.isPicked(r.runId)) { this.cmp = { show: false, a: "", b: "" }; this.cmpRows = []; }
        await this.load();
      } catch (e) { toast(e.message, "err"); }
    },
    async compare() {
      const checked = Array.from(document.querySelectorAll(".cmp:checked")).map(c => c.value);
      if (checked.length !== 2) { toast("选择两次运行进行对比", "err"); return; }
      try {
        const res = await Promise.all([
          api("/api/runs/" + checked[0] + "/equity"), api("/api/runs/" + checked[1] + "/equity"),
          api("/api/runs/" + checked[0] + "/metrics"), api("/api/runs/" + checked[1] + "/metrics")
        ]);
        const rowsOf = eq => eq.filter(e => e.seriesId === "strategy").map(e => ({ date: e.tradeDate, nav: e.nav }));
        const series = alignSeries([
          { name: "A", color: "#0F766E", rows: rowsOf(res[0]) },
          { name: "B", color: "#475569", dash: true, rows: rowsOf(res[1]) }
        ]);
        const byKey = m => {
          const o = {};
          m.filter(x => x.seriesId === "strategy").forEach(x => { o[x.metric] = x.metricValue; });
          return o;
        };
        const ka = byKey(res[2]), kb = byKey(res[3]);
        const order = ["total_return", "annual_return", "annual_vol", "max_drawdown", "sharpe", "turnover", "trade_count", "total_cost"];
        this.cmpRows = order.filter(k => ka[k] !== undefined || kb[k] !== undefined).map(k => {
          const a = ka[k], b = kb[k];
          let diff = "NA";
          if (a !== null && a !== undefined && b !== null && b !== undefined) {
            const d = a - b;
            if (["total_return", "annual_return", "max_drawdown"].includes(k)) {
              const cls = k === "max_drawdown" ? (d > 0 ? "pos" : "neg") : signedClass(d);
              diff = '<span class="' + cls + '">' + (d > 0 ? "+" : "") + fmtPct(d, 2) + "</span>";
            } else diff = fmtNum(d, 3);
          }
          return { key: k, label: METRIC_LABELS[k] || k, a, b, fmt: METRIC_FMT[k] || (v => fmtNum(v)), diff };
        });
        this.cmp = { show: true, a: checked[0], b: checked[1] };
        await this.$nextTick();
        if (!this.$refs.cmpChart) return;
        const n = series.length ? series[0].points.length : 0;
        renderLineChart(this.$refs.cmpChart, {
          series, height: this.chartHeight, legend: false,
          xLabel: n <= 40 ? (d => d.slice(5)) : (d => d.slice(0, 7)), yFmt: v => v.toFixed(3)
        });
        this.$refs.cmpChart.scrollIntoView({ behavior: "smooth", block: "nearest" });
      } catch (e) { toast(e.message, "err"); }
    },
    aiContext() {
      return "【运行历史页】共 " + this.runs.length + " 次运行:\n" + this.runs.slice(0, 8).map(r =>
        "- " + r.runId + " " + r.purpose + " " + (r.startDate || "") + "~" + (r.endDate || "") + " " + r.status
        + (r.note ? "(" + r.note.slice(0, 40) + ")" : "")).join("\n");
    }
  }
};

/* 两条区间不同的净值按日期对齐:缺失日记 null,避免按序号错位比较 */
function alignSeries(list) {
  const dates = Array.from(new Set([].concat.apply([], list.map(x => x.rows.map(r => r.date))))).sort();
  return list.map(x => {
    const m = {};
    x.rows.forEach(r => { m[r.date] = r.nav; });
    return { name: x.name, color: x.color, dash: x.dash,
      points: dates.map(d => ({ label: d, value: m[d] === undefined ? null : m[d] })) };
  });
}

/* ================= 根组件 + 哈希路由 ================= */
const ROUTES = {
  overview: { view: OverviewView, title: "概览" },
  data: { view: DataPage, title: "数据管理" },
  factors: { view: FactorsPage, title: "因子库" },
  strategy: { view: StrategyPage, title: "策略配置" },
  backtest: { view: BacktestPage, title: "回测运行" },
  result: { view: ResultPage, title: "回测结果" },
  history: { view: HistoryPage, title: "运行历史" }
};

function parseHash() {
  const h = (location.hash || "#/overview").replace(/^#\//, "");
  const [name, query] = h.split("?");
  const params = {};
  if (query) new URLSearchParams(query).forEach((v, k) => params[k] = v);
  return { name: ROUTES[name] ? name : "overview", params };
}

const app = Vue.createApp({
  data() {
    return {
      route: parseHash(),
      ready: false, navOpen: false,
      modal: MODAL,
      ai: { open: false, configured: true, loading: false, input: "", msgs: [],
        permission: true, pos: null, dragging: false, dragOff: { x: 0, y: 0 }, pending: null }
    };
  },
  computed: {
    navItems() {
      return [
        { name: "overview", icon: "grid", label: "概览" },
        { name: "data", icon: "database", label: "数据管理" },
        { name: "factors", icon: "function", label: "因子库" },
        { name: "strategy", icon: "sliders", label: "策略配置" },
        { name: "backtest", icon: "play", label: "回测运行" },
        { name: "history", icon: "list", label: "运行历史" }
      ];
    },
    currentView() { return ROUTES[this.route.name].view; },
    currentTitle() { return ROUTES[this.route.name].title; },
    aiState() { return this.ai.configured ? (this.ai.permission ? "可操作" : "仅分析") : "未配置"; },
    viewKey() { return this.route.name + ":" + JSON.stringify(this.route.params); },
    panelStyle() {
      return this.ai.pos
        ? { left: this.ai.pos.x + "px", top: this.ai.pos.y + "px", right: "auto", bottom: "auto" }
        : null;
    },
    suggestions() {
      const p = this.route.name;
      const map = {
        overview: ["检查当前系统是否具备回测条件", "总结最近的回测结果"],
        data: ["检查所有 ETF 的行情数据质量", "找出行情覆盖不足或异常的 ETF"],
        factors: ["解释三个因子的公式和适用场景", "动量排名对策略意味着什么?"],
        strategy: ["帮我生成一个月度调仓的稳健方案", "解释当前策略参数的作用"],
        backtest: ["运行前帮我检查日期和数据覆盖", "设计一个动量窗口敏感性实验"],
        result: ["解释本次回测的表现与风险", "分析最大回撤发生在什么区间", "生成完整研究报告"],
        history: ["比较最近两次成功回测的结果", "总结这些实验说明什么"]
      };
      return map[p] || [];
    }
  },
  mounted() {
    window.addEventListener("hashchange", () => { this.route = parseHash(); this.navOpen = false; });
    api("/api/ai/configured").then(r => { this.ai.configured = !!r.configured; }).catch(() => {});
    if (parseHash().params.ai === "1") this.ai.open = true;   // #/overview?ai=1 直接打开面板(演示用)
    this.$nextTick(() => { this.ready = true; });   // 先挂载外壳,结果页才能把操作传送进顶栏
    document.addEventListener("click", e => {
      const b = e.target.closest("[data-ai-act]");
      if (b) {
        // 确认卡片内的按钮由 v-html 生成,这里统一委托
        if (b.dataset.aiAct === "confirm") this.confirmAi();
        else if (b.dataset.aiAct === "cancel") this.cancelAi();
        else if (b.dataset.aiAct === "dl") {
          const a = document.createElement("a");
          a.href = b.dataset.aiUrl;
          a.download = "";
          a.click();
        }
        return;
      }
      // 点击其他位置时收起菜单与窄屏导航
      if (!e.target.closest("details.menu")) closeMenus();
      if (this.navOpen && !e.target.closest(".sheet-nav") && !e.target.closest(".nav-toggle")) this.navOpen = false;
    });
    document.addEventListener("keydown", e => {
      if (e.key !== "Escape") return;
      if (MODAL.open) closeModal();
      this.navOpen = false;
    });
  },
  watch: {
    "modal.open"(v) {
      if (!v) return;
      this.$nextTick(() => { if (this.$refs.modalCard) this.$refs.modalCard.focus(); });
    }
  },
  methods: {
    // 模板渲染时不能直接访问全局函数,这里注册为组件方法
    closeModal,
    closeMenus,
    go(name) { location.hash = "#/" + name; },
    goResult(runId) { location.hash = "#/result?run_id=" + runId; },
    currentContext() {
      let s = "[当前页面:" + this.route.name + "]\n";
      try {
        const v = this.$refs.view;
        if (v && typeof v.aiContext === "function") s += v.aiContext();
      } catch (e) { /* 忽略 */ }
      return s;
    },
    openAi() {
      this.ai.open = true;
      api("/api/ai/configured").then(r => { this.ai.configured = !!r.configured; }).catch(() => {});
    },
    /* ===== 拖动面板 ===== */
    aiDragStart(e) {
      if (e.target.closest("button")) return;   // 头部按钮不触发拖动
      const panel = this.$refs.aiPanel;
      if (!panel) return;
      const r = panel.getBoundingClientRect();
      this.ai.dragOff = { x: e.clientX - r.left, y: e.clientY - r.top };
      this.ai.dragging = true;
      const w = r.width, h = r.height;
      const move = ev => {
        if (!this.ai.dragging) return;
        let x = ev.clientX - this.ai.dragOff.x;
        let y = ev.clientY - this.ai.dragOff.y;
        x = Math.max(8, Math.min(window.innerWidth - w - 8, x));
        y = Math.max(8, Math.min(window.innerHeight - 80, y));
        this.ai.pos = { x: Math.round(x), y: Math.round(y) };
      };
      const up = () => {
        this.ai.dragging = false;
        window.removeEventListener("mousemove", move);
        window.removeEventListener("mouseup", up);
      };
      window.addEventListener("mousemove", move);
      window.addEventListener("mouseup", up);
    },
    /* ===== 工具执行(白名单) ===== */
    waitBus(key) {
      return new Promise(resolve => {
        let n = 0;
        const t = setInterval(() => {
          if (AI_BUS[key] || ++n > 20) { clearInterval(t); resolve(AI_BUS[key]); }
        }, 150);
      });
    },
    async executeTool(name, a) {
      a = a || {};
      const readOnly = name === "get_run_detail";
      if (!readOnly && name !== "navigate" && !this.ai.permission) {
        return "权限未开启:未执行「" + name + "」(点击面板右上角的锁形按钮开启)";
      }
      try {
        switch (name) {
          case "update_strategy_form": {
            if (!AI_BUS.strategyForm) { this.go("strategy"); await this.waitBus("strategyForm"); }
            const f = AI_BUS.strategyForm;
            if (!f) return "策略表单暂不可用";
            const allow = ["momentumWindow", "holdingCount", "rebalanceFreq", "defensiveEnabled",
              "defensiveThreshold", "defensiveMode", "defensiveAsset", "trendFilterEnabled", "trendWindow",
              "trendThreshold", "commissionRate", "minimumCommission", "slippageRate", "initialCash",
              "benchmarkAsset", "name"];
            const changes = [];
            for (const k of allow) {
              if (a[k] !== undefined && a[k] !== null) {
                changes.push(k + " " + f[k] + "→" + a[k]);
                f[k] = a[k];
              }
            }
            return changes.length ? "策略表单已更新(" + changes.join(";") + ")" : "没有需要更新的字段";
          }
          case "set_backtest_dates": {
            if (!AI_BUS.backtestForm) { this.go("backtest"); await this.waitBus("backtestForm"); }
            const r = AI_BUS.backtestForm;
            if (!r) return "回测日期表单暂不可用";
            const ch = [];
            if (a.start) { ch.push("开始 " + (r.start || "?") + "→" + a.start); r.start = a.start; }
            if (a.end) { ch.push("结束 " + (r.end || "?") + "→" + a.end); r.end = a.end; }
            return ch.length ? "回测日期已设置(" + ch.join(";") + ")" : "未提供日期";
          }
          case "save_and_run_backtest": {
            if (!AI_BUS.strategyForm) { this.go("strategy"); await this.waitBus("strategyForm"); }
            const f = AI_BUS.strategyForm;
            if (!f) return "策略表单暂不可用";
            const save = await api("/api/strategies", { method: "POST", body: JSON.stringify(f) });
            const d = AI_BUS.backtestForm || {};
            const body = { configId: save.id, start: d.start || "2019-01-01",
              end: d.end || new Date().toISOString().slice(0, 10), purpose: "historical_backtest",
              note: a.note || ("AI 助手执行:" + save.name + " v" + save.version) };
            const res = await api("/api/backtest/run", { method: "POST", body: JSON.stringify(body) });
            if (res.status === "SUCCESS") {
              this.goResult(res.runId);
              return "已保存 " + save.name + " v" + save.version + " 并运行回测:" + res.message + "(run_id=" + res.runId + ",已跳转结果页)";
            }
            return "回测失败:" + res.message;
          }
          case "run_sample_check": {
            const res = await api("/api/backtest/sample-check", { method: "POST" });
            const p = res.checks.filter(c => c.pass).length;
            this.goResult(res.runId);
            return "样本复算完成:" + p + "/" + res.checks.length + " 项通过(run_id=" + res.runId + ",已跳转结果页)";
          }
          case "refresh_market_data": {
            const res = a.code
              ? await api("/api/data/refresh/" + a.code, { method: "POST" })
              : await api("/api/data/refresh-all", { method: "POST" });
            return "行情抓取:" + (res.message || JSON.stringify(res).slice(0, 200));
          }
          case "add_etf": {
            const res = await api("/api/data/add-etf", { method: "POST", body: JSON.stringify({ code: a.code }) });
            return "添加ETF:" + res.message;
          }
          case "navigate": {
            const page = ["overview", "data", "factors", "strategy", "backtest", "history"].includes(a.page) ? a.page : "overview";
            this.go(page);
            return "已切换到页面 " + page;
          }
          case "get_run_detail": {
            let runId = a.runId;
            if (!runId) {
              const runs = await api("/api/runs");
              const hist = runs.find(r => r.purpose !== "sample_check" && r.status === "SUCCESS");
              if (!hist) return "没有可查询的历史回测运行";
              runId = hist.runId;
            }
            const d = await api("/api/runs/" + runId);
            const met = await api("/api/runs/" + runId + "/metrics");
            const s = {};
            met.filter(m => m.seriesId === "strategy").forEach(m => s[m.metric] = m.metricValue);
            return "run_id=" + runId + " " + (d.run.startDate || "") + "~" + (d.run.endDate || "")
              + " 策略指标:" + JSON.stringify(s);
          }
          default:
            return "未知工具:" + name;
        }
      } catch (e) {
        return "工具执行出错:" + e.message;
      }
    },
    /* ===== 问答(工具分级:服务端计算 → L0/L1 直接执行 → L2 需确认) ===== */
    async sendAi(preset) {
      const q = (preset || this.ai.input || "").trim();
      if (!q || this.ai.loading || this.ai.pending) return;
      this.ai.input = "";
      this.ai.msgs.push({ role: "user", html: esc(q) });
      this.ai.loading = true;
      this.scrollAi();
      const base = {
        question: q,
        context: this.currentContext(),
        history: this.ai.msgs.filter(m => m.plain).slice(-8).map(m => ({ role: m.role, content: m.plain }))
      };
      try {
        let res = await api("/api/ai/chat", {
          method: "POST", body: JSON.stringify(Object.assign({ tools: this.ai.permission }, base))
        });
        let guard = 0;
        while (res.pending && guard++ < 3) {
          const fe = res.toolCalls || [];
          const l2 = fe.filter(t => L2_TOOLS.has(t.name));
          if (l2.length) {
            // 含持久化/资源操作:展示确认卡片,等待用户决定
            this.ai.pending = { base, res };
            this.ai.msgs.push({ role: "ai", html: this.confirmCardHtml(fe) });
            this.ai.loading = false;
            this.scrollAi();
            return;
          }
          res = await this.dispatchTools(base, res);
        }
        this.finishAi(res);
      } catch (e) {
        this.ai.msgs.push({ role: "ai", html: '<span class="muted">' + esc(e.message) + "</span>" });
      } finally {
        this.ai.loading = false;
        this.scrollAi();
      }
    },
    /* 执行本轮前端工具并回传(含服务端工具结果中继),返回下一轮响应 */
    async dispatchTools(base, res) {
      this.noteBackend(res);
      const results = [];
      for (const tc of (res.toolCalls || [])) {
        const out = await this.executeTool(tc.name, tc.arguments);
        results.push({ id: tc.id, content: out });
        this.ai.msgs.push({ role: "ai", html: "<b>已执行</b>：" + mdLite(String(out).slice(0, 400)) });
        this.scrollAi();
      }
      const backendResults = res.backendResults || [];
      const allCalls = (res.backendToolCalls || []).concat(res.toolCalls || []);
      const allResults = backendResults.concat(results);
      return await api("/api/ai/chat", {
        method: "POST", body: JSON.stringify(Object.assign({ toolCalls: allCalls, toolResults: allResults }, base))
      });
    },
    noteBackend(res) {
      const bc = res.backendToolCalls || [];
      for (const t of bc) {
        this.ai.msgs.push({ role: "ai", html: "<b>已计算</b>：" + esc(TOOL_LABELS[t.name] || t.name) });
      }
      // 报告生成完毕提供下载
      for (const t of bc) {
        if (t.name === "generate_report") {
          const br = (res.backendResults || []).find(r => String(r.content || "").indexOf("/api/runs/") >= 0);
          const m = br ? String(br.content).match(/\/api\/runs\/[\w]+\/export\/report\.md/) : null;
          if (m) {
            this.ai.msgs.push({ role: "ai",
              html: "<button class='btn sm' data-ai-act='dl' data-ai-url='" + m[0] + "'>下载 report.md</button>"
                + " &nbsp;<a class='small' href='" + m[0] + "' target='_blank'>在新窗口打开</a>" });
          }
        }
      }
      this.scrollAi();
    },
    confirmCardHtml(fe) {
      let rows = "";
      for (const t of fe) {
        const l2 = L2_TOOLS.has(t.name);
        rows += "<div style='display:flex;gap:8px;align-items:baseline'>"
          + "<span class='badge " + (l2 ? "amber" : "gray") + "'>" + (l2 ? "需确认" : "直接执行") + "</span>"
          + "<span>" + esc(TOOL_LABELS[t.name] || t.name) + "</span>"
          + "<span class='muted small mono'>" + esc(JSON.stringify(t.arguments || {})).slice(0, 120) + "</span></div>";
      }
      return "<div class='ai-confirm'><b>操作确认</b>：AI 请求执行以下操作<div style='margin:8px 0'>"
        + rows + "</div><div class='toolbar' style='margin-top:10px'>"
        + "<button class='btn sm' data-ai-act='confirm'>确认执行</button>"
        + "<button class='btn sm ghost' data-ai-act='cancel'>取消</button></div>";
    },
    async confirmAi() {
      const p = this.ai.pending;
      if (!p) return;
      this.ai.pending = null;
      this.ai.loading = true;
      try {
        let res = await this.dispatchTools(p.base, p.res);
        let guard = 0;
        while (res.pending && guard++ < 3) {
          const fe = res.toolCalls || [];
          if (fe.some(t => L2_TOOLS.has(t.name))) {
            this.ai.pending = { base: p.base, res };
            this.ai.msgs.push({ role: "ai", html: this.confirmCardHtml(fe) });
            this.ai.loading = false;
            this.scrollAi();
            return;
          }
          res = await this.dispatchTools(p.base, res);
        }
        this.finishAi(res);
      } catch (e) {
        this.ai.msgs.push({ role: "ai", html: '<span class="muted">' + esc(e.message) + "</span>" });
      } finally {
        this.ai.loading = false;
        this.scrollAi();
      }
    },
    async cancelAi() {
      const p = this.ai.pending;
      if (!p) return;
      this.ai.pending = null;
      this.ai.loading = true;
      try {
        const results = (p.res.toolCalls || []).map(t => ({ id: t.id, content: "用户取消了该操作,未执行" }));
        const backendResults = p.res.backendResults || [];
        const allCalls = (p.res.backendToolCalls || []).concat(p.res.toolCalls || []);
        const res = await api("/api/ai/chat", {
          method: "POST",
          body: JSON.stringify(Object.assign({ toolCalls: allCalls, toolResults: backendResults.concat(results) }, p.base))
        });
        this.finishAi(res);
      } catch (e) {
        this.ai.msgs.push({ role: "ai", html: '<span class="muted">' + esc(e.message) + "</span>" });
      } finally {
        this.ai.loading = false;
        this.scrollAi();
      }
    },
    finishAi(res) {
      if (res && res.answer) {
        this.ai.msgs.push({ role: "ai", html: mdLite(res.answer), plain: res.answer });
      }
    },
    newAiChat() {
      this.ai.msgs = [];
      this.ai.pending = null;
    },
    scrollAi() {
      this.$nextTick(() => {
        const el = this.$refs.aiMsgs;
        if (el) el.scrollTop = el.scrollHeight;
      });
    }
  }
});
/* 线性图标(24 网格,描边 1.7) */
const ICONS = {
  trend: '<path d="M3.5 16.5l5-5 3.5 3.5 8.5-8.5"/><path d="M14.5 6.5h6v6"/>',
  grid: '<rect x="3.5" y="3.5" width="7" height="7" rx="2"/><rect x="13.5" y="3.5" width="7" height="7" rx="2"/><rect x="3.5" y="13.5" width="7" height="7" rx="2"/><rect x="13.5" y="13.5" width="7" height="7" rx="2"/>',
  database: '<ellipse cx="12" cy="5.5" rx="7.5" ry="3"/><path d="M4.5 5.5v6.5c0 1.7 3.4 3 7.5 3s7.5-1.3 7.5-3V5.5"/><path d="M4.5 12v6.5c0 1.7 3.4 3 7.5 3s7.5-1.3 7.5-3V12"/>',
  "function": '<path d="M15.5 4.5h-2c-1.7 0-2.7 1-3 2.7l-1.8 10.3c-.3 1.5-1.1 2-2.2 2"/><path d="M6.5 10h7.5"/>',
  sliders: '<path d="M4 7h9M17 7h3M4 12h3M11 12h9M4 17h9M17 17h3"/><circle cx="15" cy="7" r="2"/><circle cx="9" cy="12" r="2"/><circle cx="15" cy="17" r="2"/>',
  play: '<path d="M8 5.5v13l10.5-6.5z"/>',
  list: '<path d="M9 6h11M9 12h11M9 18h11"/><circle cx="4.5" cy="6" r="1" fill="currentColor" stroke="none"/><circle cx="4.5" cy="12" r="1" fill="currentColor" stroke="none"/><circle cx="4.5" cy="18" r="1" fill="currentColor" stroke="none"/>',
  spark: '<path d="M11 3.5l1.9 4.9 4.9 1.9-4.9 1.9L11 17.1 9.1 12.2 4.2 10.3l4.9-1.9z"/><path d="M18.5 14.5l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z"/>',
  download: '<path d="M12 4.5v10.5M7.5 10.5l4.5 4.5 4.5-4.5"/><path d="M5 19.5h14"/>',
  refresh: '<path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3"/><path d="M19.5 4v4.5H15"/>',
  save: '<path d="M5.5 5.5h10.5l2.5 2.5v10.5h-13z"/><path d="M8.5 5.5v3.5h6V5.5"/><rect x="8.5" y="13" width="7" height="5"/>',
  up: '<path d="M3.5 16.5l5-5 3.5 3.5 8.5-8.5"/><path d="M14.5 6.5h6v6"/>',
  down: '<path d="M3.5 7.5l5 5 3.5-3.5 8.5 8.5"/><path d="M14.5 17.5h6v-6"/>',
  bars: '<path d="M6 19.5v-7M12 19.5v-13M18 19.5v-10"/>',
  coins: '<ellipse cx="12" cy="6.5" rx="7" ry="3"/><path d="M5 6.5v5.5c0 1.7 3.1 3 7 3s7-1.3 7-3V6.5"/><path d="M5 12v5.5c0 1.7 3.1 3 7 3s7-1.3 7-3V12"/>',
  layers: '<path d="M12 3.5l8.5 4.5L12 12.5 3.5 8z"/><path d="M4.5 12.5L12 16.5l7.5-4M4.5 16.5L12 20.5l7.5-4"/>',
  trash: '<path d="M5 7h14M9.5 7V4.5h5V7M7 7l1 12.5h8L17 7"/>',
  lock: '<rect x="5.5" y="10.5" width="13" height="9.5" rx="2.5"/><path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5"/>',
  unlock: '<rect x="5.5" y="10.5" width="13" height="9.5" rx="2.5"/><path d="M8.5 10.5V8a3.5 3.5 0 0 1 6.6-1.6"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  send: '<path d="M20 4.5L10 14.5"/><path d="M20 4.5l-6 15-4-5-5-4z"/>',
  chevron: '<path d="M9 5.5l6.5 6.5L9 18.5"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  menu: '<path d="M5 7h14M5 12h14M5 17h14"/>'
};
const Icon = {
  props: { name: { type: String, required: true }, size: { type: [Number, String], default: 18 } },
  computed: { path() { return ICONS[this.name] || ""; } },
  template: `<svg class="ico" :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"
    v-html="path"></svg>`
};
app.component("v-icon", Icon);
app.mount("#app");
