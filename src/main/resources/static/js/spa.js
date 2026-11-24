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

const MODAL = Vue.reactive({ open: false, title: "", body: "", width: null });
function openModal(title, body, width) {
  MODAL.title = title; MODAL.body = body; MODAL.width = width || null; MODAL.open = true;
}

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
const helpers = { methods: { fmtPct, fmtNum, fmtMoney, fmtQty, signedClass } };

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

function badgeRun(r) {
  return r.purpose === "sample_check"
    ? '<span class="badge amber">样本复算</span>' : '<span class="badge teal">历史回测</span>';
}
function badgeStatus(s) {
  return s === "SUCCESS" ? '<span class="badge green">SUCCESS</span>'
    : '<span class="badge red">' + esc(s) + "</span>";
}

/* ================= 视图:概览 ================= */
const OverviewView = {
  mixins: [helpers],
  template: `
  <div>
    <div class="hero">
      <div class="eyebrow">COURSE PROJECT · T03.01 相对动量轮动</div>
      <h2>ETF 相对动量是否延续?扣除切换成本后表现如何?</h2>
      <p>每周最后一个交易日收盘后,对候选池 ETF 按 W 日动量排名,持有前 K 只;防御开关触发时转入现金/防御资产。信号 T 日 15:05 产生,T+1 09:30 开盘价含滑点成交,与单资产买入持有对照。</p>
      <div class="chips">
        <span class="chip">Vue 3 前端</span><span class="chip">Spring Boot 3.3</span>
        <span class="chip">MyBatis</span><span class="chip">东方财富行情</span><span class="chip">AI 助手 · GLM</span>
      </div>
    </div>

    <div class="callout" v-if="banner" v-html="banner"></div>

    <div class="grid c4">
      <div class="stat"><div class="row1"><span class="k">真实 ETF 资产</span><span class="icon" style="background:#e6f5f2">🗂️</span></div>
        <div class="v">{{ ov.realEtfCount || "-" }}<small> 只</small></div></div>
      <div class="stat"><div class="row1"><span class="k">行情记录总数</span><span class="icon" style="background:#e3ecf3">📊</span></div>
        <div class="v">{{ (ov.barCount||0).toLocaleString('zh-CN') }}</div></div>
      <div class="stat"><div class="row1"><span class="k">数据覆盖</span><span class="icon" style="background:#fef3c7">📅</span></div>
        <div class="v" style="font-size:15px;padding-top:6px">{{ ov.coverageStart ? ov.coverageStart + ' ~ ' + ov.coverageEnd : '未下载' }}</div></div>
      <div class="stat"><div class="row1"><span class="k">回测运行次数</span><span class="icon" style="background:#dcfce7">▶️</span></div>
        <div class="v">{{ ov.runCount || 0 }}</div></div>
    </div>

    <div class="card" style="margin-top:16px">
      <h3><span class="dot"></span>快速开始</h3>
      <div class="grid c4">
        <button class="btn" @click="$root.go('data')">① 下载行情数据</button>
        <button class="btn ghost" @click="$root.go('backtest')">② 运行历史回测</button>
        <button class="btn ghost" @click="runSample">③ B 样本复算</button>
        <button class="btn ghost" @click="$root.go('history')">④ 查看运行历史</button>
      </div>
    </div>

    <div class="card">
      <h3><span class="dot"></span>最近运行</h3>
      <div class="tbl-wrap" v-if="runs.length">
        <table class="tbl"><thead><tr><th>run_id</th><th>目的</th><th>数据</th><th>区间</th><th>状态</th><th>时间</th></tr></thead>
        <tbody>
          <tr v-for="r in runs.slice(0,6)" :key="r.runId" class="clickable" @click="$root.goResult(r.runId)">
            <td class="mono">{{ r.runId }}</td><td v-html="badgeRun(r)"></td>
            <td>{{ r.dataType === 'synthetic' ? '虚构' : '真实' }}</td>
            <td>{{ (r.startDate||'') + ' ~ ' + (r.endDate||'') }}</td>
            <td v-html="badgeStatus(r.status)"></td>
            <td class="muted">{{ (r.createdAt||'').replace('T',' ').slice(0,19) }}</td>
          </tr>
        </tbody></table>
      </div>
      <div v-else class="callout warn" style="margin:0">暂无运行记录 —— 先跑一次「B 样本复算」验证引擎口径</div>
    </div>
  </div>`,
  data() { return { ov: {}, runs: [], banner: "" }; },
  async mounted() { await this.reload(); },
  methods: {
    badgeRun, badgeStatus,
    async reload() {
      try {
        this.ov = await api("/api/overview");
        this.runs = await api("/api/runs");
        const o = this.ov;
        if (!o.dataReady) {
          this.banner = "<b>行情数据尚未就绪</b> —— 请先前往 <a href='#/data'>数据管理</a> 一键抓取候选池行情(或添加任意 ETF 代码抓取)";
        } else if (!o.sampleCheckPassed) {
          this.banner = "<b>数据已就绪</b> —— 建议先执行一次「B 样本复算」,验证引擎与指南手工演算一致";
        } else {
          this.banner = "<b>✓ 样本复算已通过</b> —— 引擎口径与《实验要求》6.6 手工演算一致,可放心运行真实回测";
        }
      } catch (e) { toast(e.message, "err"); }
    },
    async runSample() {
      toast("正在运行样本复算…");
      try {
        const res = await api("/api/backtest/sample-check", { method: "POST" });
        const pass = res.checks.filter(c => c.pass).length;
        toast("样本复算完成:" + pass + "/" + res.checks.length + " 项通过", pass === res.checks.length ? "ok" : "err");
        this.$root.goResult(res.runId);
      } catch (e) { toast(e.message, "err"); }
    },
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
      <h3><span class="dot"></span>行情数据抓取(东方财富 · 日频 · 前复权)</h3>
      <div class="form-row" style="align-items:flex-end">
        <div class="field"><label>添加任意 ETF(6 位代码)</label>
          <div style="display:flex;gap:8px">
            <input v-model="addCode" placeholder="如 512690" style="width:120px" @keydown.enter="addEtf">
            <button class="btn sm" :disabled="adding" @click="addEtf">{{ adding ? '抓取中…' : '＋ 添加并抓取' }}</button>
          </div>
        </div>
        <button class="btn ghost" @click="aiCheckData">🤖 AI 检查行情数据质量</button>
        <button class="btn" :disabled="busy" @click="refreshAll">{{ busy ? '抓取中,请稍候…' : '⟳ 一键抓取全部候选池' }}</button>
        <label class="btn ghost" style="cursor:pointer">⬆ 导入 CSV
          <input type="file" accept=".csv" style="display:none" @change="importCsv">
        </label>
        <span class="small muted" style="padding-bottom:9px">CSV 格式:code,date,open,high,low,close,volume,amount(UTF-8)</span>
      </div>
      <div class="fetch-log" v-if="logLines.length">
        <div v-for="(l, i) in logLines" :key="i"><span class="ts">{{ l.ts }}</span><span :class="l.ok ? 'ok' : 'fail'">{{ l.text }}</span></div>
      </div>
      <div style="height:12px"></div>
      <div class="tbl-wrap">
        <table class="tbl"><thead><tr>
          <th>代码</th><th>名称</th><th>市场</th><th>类别</th><th>标记</th><th>来源</th>
          <th>覆盖区间</th><th>条数</th><th>操作</th>
        </tr></thead><tbody>
          <tr v-for="r in etfs" :key="r.code" :class="{ clickable: r.barCount > 0 }" @click="preview(r.code)">
            <td><b>{{ r.code }}</b></td><td>{{ r.name || '-' }}</td><td>{{ r.market }}</td><td>{{ r.category || '-' }}</td>
            <td><span v-if="r.benchmark" class="badge teal">基准</span>
                <span v-if="r.defensive" class="badge amber">防御</span>
                <span v-if="!r.enabled" class="badge gray">停用</span></td>
            <td><span :class="'badge ' + srcBadge(r.source)">{{ srcLabel(r.source) }}</span></td>
            <td class="mono">{{ r.coverageStart ? r.coverageStart + ' ~ ' + r.coverageEnd : '无数据' }}</td>
            <td class="num">{{ (r.barCount||0).toLocaleString('zh-CN') }}</td>
            <td v-if="r.source === 'DEMO'">-</td>
            <td v-else><button class="btn sm" @click.stop="refreshOne(r.code, $event)">更新</button></td>
          </tr>
        </tbody></table>
      </div>
      <p class="hint">点击行可预览最近收盘价走势。抓取来源:东方财富 push2his 接口(klt=101 日线,fqt=1 前复权),
        抓取后同时落盘 data/{code}.csv 供复现;停牌/缺失日无记录,因子计算按「窗口不足不参与排名」处理。
        DEMO_A 为《实验要求》虚构教学资产,仅用于 B 样本复算。</p>
    </div>

    <div class="card" v-if="pv.bars && pv.bars.length">
      <h3><span class="dot"></span>{{ pv.name }}({{ pv.code }})· 最近收盘价</h3>
      <div ref="pvChart"></div>
      <div class="tbl-wrap" style="margin-top:12px;max-height:300px">
        <table class="tbl"><thead><tr><th>日期</th><th>开盘</th><th>最高</th><th>最低</th><th>收盘</th><th>成交量(手)</th><th>成交额(元)</th></tr></thead>
        <tbody><tr v-for="b in pv.bars.slice(-10).reverse()" :key="b.date">
          <td>{{ b.date }}</td><td class="num">{{ fmtNum(b.open,3) }}</td><td class="num">{{ fmtNum(b.high,3) }}</td>
          <td class="num">{{ fmtNum(b.low,3) }}</td><td class="num">{{ fmtNum(b.close,3) }}</td>
          <td class="num">{{ fmtQty(b.volume) }}</td><td class="num">{{ fmtMoney(b.amount) }}</td>
        </tr></tbody></table>
      </div>
    </div>
  </div>`,
  data() {
    return { etfs: [], addCode: "", adding: false, busy: false, logLines: [], pv: {} };
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
    async refreshOne(code, ev) {
      if (ev) { const b = ev.target; b.disabled = true; b.textContent = "抓取中…"; }
      try {
        const res = await api("/api/data/refresh/" + code, { method: "POST" });
        this.log(res.message, true);
        toast(res.message, "ok");
        this.load();
      } catch (e) { this.log(code + " 抓取失败:" + e.message, false); toast(code + " 失败:" + e.message, "err"); }
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
            series: [{ name: "收盘价", color: "#0f766e", points: this.pv.bars.map(b => ({ label: b.date.slice(5), value: b.close })) }],
            height: 250, yFmt: v => v.toFixed(3)
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
      <h3><span class="dot"></span>{{ f.factorName }}
        <span class="badge teal">{{ f.factorCategory }}</span>
        <span class="badge gray mono">{{ f.factorId }}</span></h3>
      <div class="grid c2">
        <div>
          <p class="mt0"><b>公式</b>:<span class="mono">{{ f.formula }}</span></p>
          <p><b>含义</b>:{{ f.meaning }}</p>
          <p><b>参数</b>:<span class="mono small">{{ f.calcParams || '无' }}</span></p>
          <p><b>定义来源</b>:
            <a v-if="f.sourceUrl" :href="f.sourceUrl" target="_blank" rel="noopener">{{ f.sourceName }}</a>
            <template v-else>{{ f.sourceName || '-' }}</template><br>
            <span class="small muted">{{ f.sourceDetail || '' }}</span></p>
        </div>
        <div>
          <p class="mt0"><b>缺失值</b>:{{ f.missingPolicy || '留空' }}</p>
          <p><b>数据口径</b>:{{ f.dataCaliber || '留空' }}</p>
          <p><b>使用限制</b>:{{ f.limits || '留空' }}</p>
          <button class="btn sm ghost" @click="showDetail(f)">查看 22 列完整定义</button>
        </div>
      </div>
    </div>

    <div class="card">
      <h3><span class="dot"></span>因子实时计算预览(与回测引擎同一实现)</h3>
      <div class="form-row">
        <div class="field"><label>资产</label><select v-model="calc.code">
          <option v-for="e in etfs.filter(e => e.barCount > 0)" :key="e.code" :value="e.code">{{ e.code }} {{ e.name }}</option>
        </select></div>
        <div class="field"><label>因子</label><select v-model="calc.factor">
          <option v-for="f in factors" :key="f.factorId" :value="f.factorId">{{ f.factorName }}</option>
        </select></div>
        <div class="field"><label>窗口(交易日)</label><input v-model.number="calc.window" type="number" min="2" max="500"></div>
        <button class="btn" style="align-self:flex-end" @click="calcFactor">计算最近 30 期</button>
      </div>
      <div ref="calcChart"></div>
      <div class="tbl-wrap" style="margin-top:12px;max-height:340px" v-if="calcRows.length">
        <table class="tbl"><thead><tr><th>日期</th><th>收盘价</th><th>因子值</th></tr></thead><tbody>
          <tr v-for="r in calcRows" :key="r.date">
            <td>{{ r.date }}</td><td class="num">{{ fmtNum(r.close, 4) }}</td>
            <td class="num"><b v-if="r.value !== null">{{ fmtNum(r.value, 6) }}</b><span v-else class="badge gray">NA</span></td>
          </tr>
        </tbody></table>
      </div>
    </div>
  </div>`,
  data() { return { factors: [], etfs: [], calc: { code: "", factor: "g01_mom_n", window: 20 }, calcRows: [] }; },
  async mounted() {
    try {
      this.factors = await api("/api/factors");
      this.etfs = await api("/api/etfs");
      const first = this.etfs.find(e => e.barCount > 0);
      if (first) this.calc.code = first.code;
    } catch (e) { toast(e.message, "err"); }
  },
  methods: {
    showDetail(f) {
      let rows = "";
      for (const [k, label] of FIELD_22) {
        const v = f[k];
        rows += "<tr><td style='width:170px;color:#64748b'>" + esc(label)
          + "</td><td class='wrap'>" + esc(v === null || v === undefined || v === "" ? "(留空)" : v) + "</td></tr>";
      }
      openModal(esc(f.factorName) + " · 22 列规范定义",
        '<div class="tbl-wrap" style="max-height:60vh"><table class="tbl"><tbody>' + rows + "</tbody></table></div>", 820);
    },
    async calcFactor() {
      if (!this.calc.code) { toast("暂无可用资产数据", "err"); return; }
      try {
        const d = await api("/api/factors/" + this.calc.factor + "/values?code=" + this.calc.code
          + "&window=" + (this.calc.window || 20) + "&limit=30");
        this.calcRows = d.rows.slice().reverse();
        this.$nextTick(() => {
          renderLineChart(this.$refs.calcChart, {
            series: [{ name: d.factorId + "(W=" + d.window + ")", color: "#0f766e",
              points: d.rows.map(r => ({ label: r.date.slice(5), value: r.value })) }],
            height: 230, yFmt: v => v.toFixed(4)
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
    <div class="card">
      <h3><span class="dot"></span>参数设置(策略编号 etf_mom_rotation)</h3>
      <div class="form-row">
        <div class="field"><label>策略名称</label><input v-model="f.name" style="min-width:220px"></div>
        <div class="field"><label>动量窗口 W(交易日)</label><input v-model.number="f.momentumWindow" type="number" min="2" max="500"></div>
        <div class="field"><label>持有数量 K(只)</label><input v-model.number="f.holdingCount" type="number" min="1" max="10"></div>
        <div class="field"><label>调仓频率</label>
          <select v-model="f.rebalanceFreq">
            <option value="WEEKLY">每周最后一个交易日</option>
            <option value="MONTHLY">每月最后一个交易日</option>
          </select></div>
      </div>
      <div class="form-row">
        <label class="checkline"><input type="checkbox" v-model="f.defensiveEnabled">防御开关</label>
        <div class="field"><label>防御阈值(最强动量低于此值)</label><input v-model.number="f.defensiveThreshold" type="number" step="0.01"></div>
        <div class="field"><label>防御模式</label>
          <select v-model="f.defensiveMode" @change="onDefMode">
            <option value="CASH">转现金</option><option value="ASSET">转防御资产</option>
          </select></div>
        <div class="field" v-show="f.defensiveMode === 'ASSET'"><label>防御资产</label>
          <select v-model="f.defensiveAsset"><option v-for="e in defensiveEtfs" :key="e.code" :value="e.code">{{ e.code }} {{ e.name }}</option></select></div>
      </div>
      <div class="form-row">
        <label class="checkline"><input type="checkbox" v-model="f.trendFilterEnabled">趋势过滤(均线比值 &lt; 阈值才可入选)</label>
        <div class="field"><label>均线窗口 d</label><input v-model.number="f.trendWindow" type="number" min="2" max="500"></div>
        <div class="field"><label>均线比值阈值</label><input v-model.number="f.trendThreshold" type="number" step="0.01"></div>
      </div>
      <div class="form-row">
        <div class="field"><label>手续费率(双边,小数)</label><input v-model.number="f.commissionRate" type="number" step="0.0001"></div>
        <div class="field"><label>最低手续费(元)</label><input v-model.number="f.minimumCommission" type="number" step="0.01"></div>
        <div class="field"><label>滑点率(计入价格)</label><input v-model.number="f.slippageRate" type="number" step="0.0001"></div>
        <div class="field"><label>初始资金(元)</label><input v-model.number="f.initialCash" type="number" min="1000"></div>
        <div class="field"><label>基准资产</label><select v-model="f.benchmarkAsset">
          <option v-for="e in realEtfs" :key="e.code" :value="e.code">{{ e.code }} {{ e.name }}</option></select></div>
      </div>
      <div class="form-row"><div class="field" style="flex:1"><label>备注</label>
        <input v-model="f.note" placeholder="版本说明,可留空" style="width:100%"></div></div>
      <button class="btn lg" :disabled="saving" @click="save">{{ saving ? '保存中…' : '💾 保存为新版本' }}</button>
      <button class="btn ghost" style="margin-left:8px" @click="previewBJson">预览 B_主策略.json</button>
    </div>

    <div class="card">
      <h3><span class="dot"></span>规则预览(随表单实时更新)</h3>
      <div class="small" style="line-height:2.1" v-html="rulePreview"></div>
    </div>

    <div class="card">
      <h3><span class="dot"></span>已保存的策略配置</h3>
      <div class="tbl-wrap" v-if="list.length">
        <table class="tbl"><thead><tr><th>名称</th><th>W</th><th>K</th><th>频率</th><th>防御</th><th>费率</th><th>滑点</th><th>基准</th><th>操作</th></tr></thead>
        <tbody><tr v-for="c in list" :key="c.id">
          <td><b>{{ c.name }}</b> <span class="badge gray">v{{ c.version }}</span></td>
          <td>{{ c.momentumWindow }}</td><td>{{ c.holdingCount }}</td>
          <td>{{ c.rebalanceFreq === 'MONTHLY' ? '月' : '周' }}</td>
          <td>{{ c.defensiveEnabled ? (c.defensiveMode === 'ASSET' ? '转' + c.defensiveAsset : '转现金') : '关' }}</td>
          <td class="num">{{ fmtPct(c.commissionRate, 2) }}</td>
          <td class="num">{{ fmtPct(c.slippageRate, 2) }}</td>
          <td>{{ c.benchmarkAsset }}</td>
          <td>
            <button class="btn sm" @click="loadCfg(c.id)">载入</button>
            <button class="btn sm ghost" @click="showBJson(c.id)">B_主策略.json</button>
            <button class="btn sm danger" @click="delCfg(c)">删除</button>
          </td>
        </tr></tbody></table>
      </div>
      <div v-else class="callout warn" style="margin:0">暂无保存的策略配置</div>
      <p class="hint">同名保存自动递增版本号;回测运行页按「名称+版本」引用,结果可追溯。</p>
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
    rulePreview() {
      const c = this.f;
      const freq = c.rebalanceFreq === "MONTHLY" ? "每月" : "每周";
      return "<b>① 入选</b>:" + freq + "最后一个交易日收盘后,候选池 ETF 按 " + c.momentumWindow + " 日动量降序取前 "
        + c.holdingCount + " 只(并列按代码升序);"
        + (c.trendFilterEnabled ? "趋势过滤:均线比值(d=" + c.trendWindow + ")须 &lt; " + c.trendThreshold : "未启用趋势过滤")
        + "<br><b>② 退出</b>:落选或防御触发时,次日开盘全部卖出;重新入选可再买入;已持有且仍在目标内不调仓(缓冲)"
        + "<br><b>③ 防御</b>:" + (c.defensiveEnabled
          ? "开启 —— 候选池为空或最强动量 &lt; " + c.defensiveThreshold + " 时,"
            + (c.defensiveMode === "ASSET" ? "全部转入 " + c.defensiveAsset : "全部转为现金") : "关闭")
        + "<br><b>④ 执行</b>:T 日 15:05 产生信号,T+1 09:30 开盘价成交;买价上浮 " + fmtPct(c.slippageRate)
        + "、卖价下调 " + fmtPct(c.slippageRate) + ";双边手续费 " + fmtPct(c.commissionRate, 4)
        + "<br><b>⑤ 持仓</b>:每只目标权重 1/" + c.holdingCount + ",按扣费后可负担最大整数份数买入,余款留现金;初始资金 "
        + (+c.initialCash).toLocaleString("zh-CN") + " 元"
        + "<br><b>⑥ 基准</b>:" + c.benchmarkAsset + " 买入持有,共同起点开盘买入、相同费用与估值口径,期末不强平";
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
    onDefMode() {},
    pct(v, d) { return (v * 100).toFixed(d === undefined ? 4 : d) + "%"; },
    async save() {
      this.saving = true;
      try {
        const res = await api("/api/strategies", { method: "POST", body: JSON.stringify(this.f) });
        toast(res.message, "ok");
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
      <h3><span class="dot"></span>运行历史回测(真实数据)</h3>
      <div class="callout" v-if="dataWarn" v-html="dataWarn"></div>
      <div class="form-row">
        <div class="field"><label>策略配置</label><select v-model="r.configId" style="min-width:260px">
          <option v-for="s in strategies" :key="s.id" :value="s.id">{{ s.name }} v{{ s.version }}(W={{ s.momentumWindow }},K={{ s.holdingCount }}{{ s.rebalanceFreq === 'MONTHLY' ? ',月调' : ',周调' }})</option>
        </select></div>
        <div class="field"><label>开始日期</label><input v-model="r.start" type="date"></div>
        <div class="field"><label>结束日期</label><input v-model="r.end" type="date"></div>
      </div>
      <div class="form-row">
        <label class="checkline"><input type="checkbox" v-model="r.zeroCost">附加对照:零费率零滑点</label>
        <div class="field"><label>覆盖:动量窗口(可选)</label><input v-model.number="r.overrideMomentumWindow" type="number" placeholder="不改留空" min="2" max="500"></div>
        <div class="field"><label>覆盖:持有数量(可选)</label><input v-model.number="r.overrideHoldingCount" type="number" placeholder="不改留空" min="1" max="10"></div>
      </div>
      <div class="form-row"><div class="field" style="flex:1"><label>运行备注</label>
        <input v-model="r.note" placeholder="如:主方案 / 敏感性实验 W=60 / 零成本对照" style="width:100%"></div></div>
      <button class="btn lg" :disabled="running" @click="run">{{ running ? '回测运行中…' : '▶ 运行回测' }}</button>
      <span class="small muted" style="margin-left:10px">信号 T 日收盘 → 成交 T+1 开盘;含滑点与手续费</span>
      <div style="height:14px"></div>
      <div :class="'callout ' + statusCls" v-if="statusMsg" v-html="statusMsg"></div>
    </div>

    <div class="card">
      <h3><span class="dot"></span>B 样本复算(引擎正确性验证)</h3>
      <p class="mt0 small">使用《实验要求》6.6 虚构教学数据 DEMO_A(8 条日线)复算「均线比值持有策略」:
        3月7日因子 0.866667 &lt; 1 → 3月10日开盘 12 元买入;3月11日因子恰等于 1 → 3月12日开盘 11 元卖出。引擎输出与手工演算逐项对照:</p>
      <button class="btn" :disabled="sampling" @click="runSample">{{ sampling ? '复算中…' : '▶ 运行样本复算' }}</button>
      <div style="height:12px"></div>
      <div v-if="checks" :class="'callout ' + (allPass ? 'ok' : 'err')">
        <b>{{ allPass ? '全部通过 ✓' : '存在差异 ✗' }}</b>({{ passCount }}/{{ checks.length }} 项)
        run_id:<span class="mono">{{ sampleRunId }}</span>
        <a :href="'#/result?run_id=' + sampleRunId">查看完整结果 →</a>
      </div>
      <div class="tbl-wrap" style="margin-top:10px" v-if="checks">
        <table class="tbl"><thead><tr><th>检查项</th><th>预期值(指南)</th><th>引擎实际值</th><th>容差</th><th>结果</th><th>说明</th></tr></thead>
        <tbody><tr v-for="c in checks" :key="c.name">
          <td class="wrap">{{ c.name }}</td>
          <td class="num">{{ c.tolerance >= 0.5 ? c.expected : (+c.expected).toFixed(6) }}</td>
          <td class="num"><b>{{ c.actual === null ? 'NA' : (c.tolerance >= 0.5 ? c.actual : (+c.actual).toFixed(6)) }}</b></td>
          <td class="num">{{ c.tolerance >= 0.5 ? '整数' : c.tolerance }}</td>
          <td><span :class="'badge ' + (c.pass ? 'green' : 'red')">{{ c.pass ? 'PASS' : 'FAIL' }}</span></td>
          <td class="wrap small muted">{{ c.hint }}</td>
        </tr></tbody></table>
      </div>
    </div>
  </div>`,
  data() {
    return { strategies: [], r: { configId: null, start: "2019-01-01", end: "", zeroCost: false,
      overrideMomentumWindow: null, overrideHoldingCount: null, note: "" },
      running: false, statusMsg: "", statusCls: "",
      sampling: false, checks: null, sampleRunId: "", dataWarn: "" };
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
      this.dataWarn = ov.dataReady
        ? "当前数据覆盖:<b>" + ov.coverageStart + " ~ " + ov.coverageEnd + "</b>,共 " + (ov.barCount || 0).toLocaleString("zh-CN") + " 条;开始日期建议不早于覆盖起点后约 2 个月(为动量窗口留预热数据)"
        : "<b>行情未就绪</b> —— 请先到 <a href='#/data'>数据管理</a> 抓取;或先运行下方 B 样本复算";
      this.strategies = await api("/api/strategies");
      if (this.strategies.length) this.r.configId = this.strategies[0].id;
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
    <div class="card" v-if="!run"><div class="callout err" style="margin:0">加载中或 run_id 无效…</div></div>
    <template v-if="run">
    <div class="card">
      <h3><span class="dot"></span>运行信息
        <span :class="'badge ' + (run.purpose === 'sample_check' ? 'amber' : 'teal')">{{ run.purpose === 'sample_check' ? '样本复算' : '历史回测' }}</span>
        <span :class="'badge ' + (run.dataType === 'synthetic' ? 'amber' : 'green')">{{ run.dataType === 'synthetic' ? '虚构教学数据' : '真实历史数据' }}</span>
        <span :class="'badge ' + (run.status === 'SUCCESS' ? 'green' : 'red')">{{ run.status }}</span>
      </h3>
      <div class="grid c2 small" style="line-height:2">
        <div>
          run_id:<span class="mono">{{ run.runId }}</span><br>
          策略:{{ run.strategyCode }} / v{{ run.strategyVersion }}<br>
          评价区间:{{ run.startDate }} ~ {{ run.endDate }}(预热数据不计入评价)<br>
          基准:{{ run.benchmark }}
        </div>
        <div>
          策略参数:{{ stratDesc }}<br>
          成本假设:手续费率 {{ fmtPct(costs.commission_rate, 4) }}(最低 {{ costs.minimum_commission || 0 }} 元),
          滑点率 {{ fmtPct(costs.slippage_rate, 4) }}(计入成交价)<br>
          <span v-if="run.note">备注:{{ run.note }}</span>
        </div>
      </div>
      <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:14px">
        <a class="btn sm" :href="'/api/runs/' + run.runId + '/export-all'">⬇ 导出全部(ZIP)</a>
        <a class="btn sm ghost" :href="'/api/runs/' + run.runId + '/export/run.json'">run.json</a>
        <a class="btn sm ghost" :href="'/api/runs/' + run.runId + '/export/config.json'">config.json</a>
        <a class="btn sm ghost" :href="'/api/runs/' + run.runId + '/export/metrics.csv'">metrics.csv</a>
        <a class="btn sm ghost" :href="'/api/runs/' + run.runId + '/export/equity.csv'">equity.csv</a>
        <a class="btn sm ghost" :href="'/api/runs/' + run.runId + '/export/positions.csv'">positions.csv</a>
        <a class="btn sm ghost" :href="'/api/runs/' + run.runId + '/export/trades.csv'">trades.csv</a>
        <a class="btn sm ghost" :href="'/api/runs/' + run.runId + '/export/decisions.csv'">decisions.csv</a>
        <button v-if="run.purpose !== 'sample_check'" class="btn sm ghost" @click="rerunZero">↻ 附加对照:零成本重跑</button>
        <span style="flex:1"></span>
        <button class="btn sm" style="background:linear-gradient(135deg,#0d6e8c,#0f766e)" @click="aiAnalyze('解释本次回测的表现与风险')">🤖 解释本次回测</button>
        <button class="btn sm ghost" @click="aiAnalyze('分析本次回测最大回撤发生在什么区间,原因是什么')">📉 分析最大回撤</button>
        <button class="btn sm ghost" @click="aiAnalyze('分析手续费对本次回测收益的影响')">💰 成本影响</button>
        <button class="btn sm ghost" @click="aiAnalyze('为本次回测生成完整研究报告')">📄 生成研究报告</button>
      </div>
    </div>

    <div class="grid c4">
      <div class="stat"><div class="row1"><span class="k">策略累计收益</span><span class="icon" style="background:#dcfce7">📈</span></div>
        <div class="v" :class="signedClass(mval('total_return','strategy'))">{{ fmtPct(mval('total_return','strategy'), 2) }}</div></div>
      <div class="stat"><div class="row1"><span class="k">基准累计收益</span><span class="icon" style="background:#e3ecf3">📊</span></div>
        <div class="v" :class="signedClass(mval('total_return','benchmark'))">{{ fmtPct(mval('total_return','benchmark'), 2) }}</div></div>
      <div class="stat"><div class="row1"><span class="k">策略最大回撤</span><span class="icon" style="background:#fee2e2">📉</span></div>
        <div class="v neg">{{ fmtPct(mval('max_drawdown','strategy'), 2) }}</div></div>
      <div class="stat"><div class="row1"><span class="k">策略总手续费</span><span class="icon" style="background:#fef3c7">💰</span></div>
        <div class="v" style="font-size:19px">{{ fmtMoney(mval('total_cost','strategy')) }}</div></div>
    </div>

    <div class="card" style="margin-top:16px">
      <h3><span class="dot"></span>净值曲线(策略 vs 基准)</h3>
      <div ref="navChart"></div>
      <p class="hint">nav = 账户权益 ÷ 评价起点交易前权益;首点为初始净值 1,首日损益与费用已计入(指南 7.3 口径)。悬停查看逐日数值。</p>
    </div>

    <div class="card">
      <h3><span class="dot"></span>指标对照</h3>
      <div class="tbl-wrap">
        <table class="tbl"><thead><tr><th>指标</th><th>策略</th><th>基准</th><th>差值(策略-基准)</th><th>单位</th></tr></thead>
        <tbody><tr v-for="row in metricRows" :key="row.key">
          <td><span :title="row.def" style="cursor:help;border-bottom:1px dashed #bbb">{{ row.label }} ⓘ</span></td>
          <td class="num">{{ row.s === null ? 'NA' : row.fmt(row.s) }}</td>
          <td class="num">{{ row.b === null ? 'NA' : row.fmt(row.b) }}</td>
          <td class="num" v-html="row.diff"></td>
          <td class="muted">{{ row.unit }}</td>
        </tr></tbody></table>
      </div>
    </div>

    <div class="card">
      <div class="tabs">
        <button :class="{ active: tab === 'trades' }" @click="tab = 'trades'">交易记录</button>
        <button :class="{ active: tab === 'decisions' }" @click="tab = 'decisions'">信号决策</button>
        <button :class="{ active: tab === 'positions' }" @click="tab = 'positions'">持仓快照</button>
        <button :class="{ active: tab === 'equity' }" @click="tab = 'equity'">净值明细</button>
      </div>

      <div v-show="tab === 'trades'">
        <p class="small muted mt0">点击任意一笔成交查看「这笔交易为什么发生」:当日因子值、排名、目标与实际执行。</p>
        <div class="tbl-wrap">
          <table class="tbl"><thead><tr><th>信号时间</th><th>成交时间</th><th>资产</th><th>方向</th><th>数量</th><th>成交价</th><th>手续费</th><th>滑点</th><th>状态</th><th>原因</th></tr></thead>
          <tbody><tr v-for="(t, i) in strategyTrades" :key="t.id || i" class="clickable" @click="explainTrade(i)">
            <td>{{ (t.signalTime || '').replace('T',' ').slice(0,16) }}</td>
            <td>{{ (t.executionTime || 'NA').replace('T',' ').slice(0,16) }}</td>
            <td><b>{{ t.assetId }}</b></td>
            <td><span :class="'badge ' + (t.side === 'buy' ? 'green' : 'red')">{{ t.side === 'buy' ? '买入' : '卖出' }}</span></td>
            <td class="num">{{ fmtQty(t.quantity) }}</td>
            <td class="num">{{ fmtNum(t.price, 4) }}</td>
            <td class="num">{{ fmtMoney(t.fee) }}</td>
            <td class="num">{{ fmtMoney(t.slippageCost) }}</td>
            <td><span :class="'badge ' + (t.status === 'FILLED' ? 'green' : 'gray')">{{ t.status === 'FILLED' ? '成交' : '未成交' }}</span></td>
            <td class="wrap small">{{ t.reason }}</td>
          </tr></tbody></table>
        </div>
      </div>

      <div v-show="tab === 'decisions'">
        <p class="small muted mt0">每个调仓信号日的决策记录;点击查看明细(因子值/排名/目标权重/防御触发)。</p>
        <div class="tbl-wrap">
          <table class="tbl"><thead><tr><th>信号日</th><th>防御触发</th><th>防御原因</th></tr></thead>
          <tbody><tr v-for="d in decisions" :key="d.id || d.signalDate" class="clickable" @click="explainDate(d.signalDate)">
            <td>{{ d.signalDate }}</td>
            <td><span v-if="d.defensiveTriggered" class="badge amber">是</span><span v-else class="muted">否</span></td>
            <td class="wrap small">{{ d.defensiveReason || '-' }}</td>
          </tr></tbody></table>
        </div>
      </div>

      <div v-show="tab === 'positions'">
        <div class="form-row"><div class="field"><label>选择日期(策略持仓)</label>
          <select v-model="posDate" style="min-width:180px" @change="loadPositions">
            <option v-for="d in posDates.slice().reverse()" :key="d" :value="d">{{ d }}</option>
          </select></div></div>
        <div class="tbl-wrap" v-if="posRows.length">
          <table class="tbl"><thead><tr><th>系列</th><th>资产</th><th>数量</th><th>权重</th></tr></thead>
          <tbody><tr v-for="p in posRows" :key="p.seriesId + p.assetId">
            <td><span :class="'badge ' + (p.seriesId === 'strategy' ? 'teal' : 'amber')">{{ p.seriesId === 'strategy' ? '策略' : '基准' }}</span></td>
            <td><b>{{ p.assetId }}</b></td><td class="num">{{ fmtQty(p.quantity) }}</td><td class="num">{{ fmtPct(p.weight, 2) }}</td>
          </tr></tbody></table>
        </div>
        <div v-else class="callout warn" style="margin:0">该日无持仓(空仓/现金)</div>
      </div>

      <div v-show="tab === 'equity'">
        <div class="tbl-wrap" style="max-height:420px">
          <table class="tbl"><thead><tr><th>日期</th><th>策略净值</th><th>基准净值</th><th>策略现金</th><th>持仓市值</th></tr></thead>
          <tbody><tr v-for="e in equityRecent" :key="e.date + e.seriesId">
            <td>{{ e.date }}</td><td class="num">{{ fmtNum(e.navS, 6) }}</td><td class="num">{{ fmtNum(e.navB, 6) }}</td>
            <td class="num">{{ fmtMoney(e.cash) }}</td><td class="num">{{ fmtMoney(e.pv) }}</td>
          </tr></tbody></table>
        </div>
      </div>
    </div>
    </template>
  </div>`,
  data() {
    return { run: null, config: {}, costs: {}, metrics: [], equity: [], trades: [], decisions: [],
      tab: "trades", posDates: [], posDate: "", posRows: [], equityRecent: [] };
  },
  computed: {
    stratDesc() {
      const s = (this.config && this.config.strategy) || {};
      if (s.momentum_window === undefined) return "均线比值持有策略(B 样本)";
      return "W=" + s.momentum_window + ",K=" + s.holding_count + ","
        + (s.rebalance_freq === "MONTHLY" ? "月调仓" : "周调仓")
        + (s.defensive && s.defensive.enabled ? ",防御开(" + (s.defensive.mode === "ASSET" ? "转" + (s.defensive.asset || "") : "转现金")
          + ",阈值 " + s.defensive.threshold + ")" : ",防御关");
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
    const runId = this.$root.route.params.run_id;
    if (!runId) return;
    try {
      const detail = await api("/api/runs/" + runId);
      this.run = detail.run;
      this.config = detail.config || {};
      this.costs = (detail.config && detail.config.costs) || {};
      this.metrics = await api("/api/runs/" + runId + "/metrics");
      this.equity = await api("/api/runs/" + runId + "/equity");
      this.trades = await api("/api/runs/" + runId + "/trades");
      this.decisions = await api("/api/runs/" + runId + "/decisions");
      this.$nextTick(() => {
        const strategy = this.equity.filter(e => e.seriesId === "strategy");
        const bench = this.equity.filter(e => e.seriesId === "benchmark");
        renderLineChart(this.$refs.navChart, {
          series: [
            { name: "策略(动量轮动)", color: "#0f766e", points: strategy.map(e => ({ label: e.tradeDate.slice(5), value: e.nav })) },
            { name: "基准(买入持有)", color: "#d97706", points: bench.map(e => ({ label: e.tradeDate.slice(5), value: e.nav })) }
          ],
          height: 340, yFmt: v => v.toFixed(3)
        });
      });
      const recent = strategy.slice(-30).reverse().map(e => {
        const b = this.equity.find(x => x.seriesId === "benchmark" && x.tradeDate === e.tradeDate);
        return { date: e.tradeDate, navS: e.nav, navB: b ? b.nav : null, cash: e.cash, pv: e.positionValue };
      });
      this.equityRecent = recent;
      const pos = await api("/api/runs/" + runId + "/positions");
      this.posDates = pos.dates || [];
      this.posDate = this.posDates.length ? this.posDates[this.posDates.length - 1] : "";
      this.posRows = pos.rows || [];
    } catch (e) { toast(e.message, "err"); }
  },
  methods: {
    mval(key, series) {
      const m = this.metrics.find(x => x.metric === key && x.seriesId === series);
      return m ? m.metricValue : null;
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
    async explainDate(date, trade) {
      try {
        const d = await api("/api/runs/" + this.run.runId + "/explain?date=" + date);
        const dec = d.decision || {};
        let html = "";
        if (trade) {
          html += '<div class="callout"><b>这笔交易</b>:' + (trade.side === "buy" ? "买入" : "卖出") + " " + esc(trade.assetId)
            + " " + fmtQty(trade.quantity) + " 份 @ " + fmtNum(trade.price, 4) + "(含滑点),手续费 " + fmtMoney(trade.fee)
            + " 元<br><b>原因</b>:" + esc(trade.reason) + "</div>";
        }
        if (dec.defensive && dec.defensive.triggered) {
          html += '<div class="callout warn"><b>防御触发</b>:' + esc(dec.defensive.reason || "") + "</div>";
        }
        const assets = dec.assets || [];
        if (assets.length) {
          html += '<p class="mt0"><b>' + esc(date) + " 收盘后候选池因子值</b>(W=" + (dec.momentum_window || "-") + ")</p>";
          html += '<div class="tbl-wrap" style="max-height:280px"><table class="tbl"><thead><tr><th>资产</th><th>动量</th><th>均线比值</th><th>可入选</th><th>排名</th></tr></thead><tbody>';
          for (const a of assets) {
            const isT = (dec.targets || []).some(x => x.code === a.code);
            html += "<tr" + (isT ? ' style="background:#e6f5f2"' : "") + "><td><b>" + esc(a.code) + "</b>"
              + (isT ? ' <span class="badge teal">目标</span>' : "") + "</td>"
              + '<td class="num">' + (a.momentum === null || a.momentum === undefined ? "NA" : fmtPct(a.momentum, 2)) + "</td>"
              + '<td class="num">' + (a.ma_ratio === null || a.ma_ratio === undefined ? "-" : fmtNum(a.ma_ratio, 4)) + "</td>"
              + "<td>" + (a.eligible ? "✓" : "✗") + "</td><td class='num'>" + (a.rank || "-") + "</td></tr>";
          }
          html += "</tbody></table></div>";
        }
        const targets = dec.targets || [];
        html += "<p style='margin-top:10px'><b>目标组合</b>:" + (targets.length
          ? targets.map(t => esc(t.code) + "(" + fmtPct(t.weight, 0) + (t.momentum !== undefined && t.momentum !== null ? ",动量 " + fmtPct(t.momentum, 2) : "") + ")").join("、")
          : "空仓(现金)") + "</p>";
        const ex = d.trades || [];
        if (ex.length) {
          html += "<p class='mt0'><b>" + esc(dec.execution_date || "") + " 开盘实际执行</b>:</p><ul class='small' style='margin-top:4px'>";
          for (const t of ex) {
            html += "<li>" + (t.side === "buy" ? "买入" : "卖出") + " " + esc(t.assetId) + " " + fmtQty(t.quantity)
              + " 份 @ " + fmtNum(t.price, 4) + ",手续费 " + fmtMoney(t.fee) + " —— " + esc(t.reason) + "</li>";
          }
          html += "</ul>";
        }
        openModal("交易解释 · " + esc(date) + " 信号", html, 780);
      } catch (e) { toast(e.message, "err"); }
    },
    rerunZero() {
      if (!confirm("将以相同区间与参数、零费率零滑点重新运行(新 run_id),用于成本影响分析。继续?")) return;
      const body = { configId: this.run.strategyConfigId, start: this.run.startDate, end: this.run.endDate,
        purpose: "historical_backtest", zeroCost: true, note: "附加对照:零费率零滑点(对照 " + this.run.runId + ")" };
      api("/api/backtest/run", { method: "POST", body: JSON.stringify(body) })
        .then(res => {
          if (res.status === "SUCCESS") { toast("零成本对照运行成功:" + res.runId, "ok"); this.$root.goResult(res.runId); }
          else toast("重跑失败:" + res.message, "err");
        })
        .catch(e => toast(e.message, "err"));
    },
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
      <div style="display:flex;gap:12px;align-items:center;margin-bottom:14px;flex-wrap:wrap">
        <button class="btn" @click="compare">⇄ 对比勾选的两条运行</button>
        <span class="small muted">适合主方案 vs 零成本对照、参数敏感性等附加比较</span>
      </div>
      <div class="tbl-wrap">
        <table class="tbl"><thead><tr>
          <th></th><th>run_id</th><th>目的</th><th>数据</th><th>区间</th><th>策略版本</th><th>状态</th><th>备注</th><th>创建时间</th><th>操作</th>
        </tr></thead><tbody>
          <tr v-for="r in runs" :key="r.runId">
            <td><input type="checkbox" class="cmp" :value="r.runId" style="accent-color:#0f766e"></td>
            <td class="mono"><a :href="'#/result?run_id=' + r.runId">{{ r.runId }}</a></td>
            <td v-html="purposeBadge(r)"></td>
            <td>{{ r.dataType === 'synthetic' ? '虚构' : '真实' }}</td>
            <td>{{ (r.startDate || '-') + ' ~ ' + (r.endDate || '-') }}</td>
            <td>{{ r.strategyCode }} v{{ r.strategyVersion }}</td>
            <td v-html="statusBadge(r.status)"></td>
            <td class="wrap small">{{ r.note || '-' }}</td>
            <td class="muted">{{ (r.createdAt || '').replace('T',' ').slice(0,19) }}</td>
            <td>
              <a class="btn sm" :href="'#/result?run_id=' + r.runId">查看</a>
              <a class="btn sm ghost" :href="'/api/runs/' + r.runId + '/export-all'">导出ZIP</a>
              <button class="btn sm danger" @click="delRun(r)">删除</button>
            </td>
          </tr>
        </tbody></table>
      </div>
    </div>

    <div class="card" v-if="cmp.show">
      <h3><span class="dot"></span>运行对比:{{ cmp.a }} vs {{ cmp.b }}</h3>
      <div ref="cmpChart"></div>
      <div class="tbl-wrap" style="margin-top:12px">
        <table class="tbl"><thead><tr><th>指标(策略序列)</th><th>{{ cmp.a }}</th><th>{{ cmp.b }}</th><th>差值(A-B)</th></tr></thead>
        <tbody><tr v-for="row in cmpRows" :key="row.key">
          <td>{{ row.label }}</td>
          <td class="num">{{ row.a === null || row.a === undefined ? 'NA' : row.fmt(row.a) }}</td>
          <td class="num">{{ row.b === null || row.b === undefined ? 'NA' : row.fmt(row.b) }}</td>
          <td class="num" v-html="row.diff"></td>
        </tr></tbody></table>
      </div>
    </div>
  </div>`,
  data() { return { runs: [], cmp: { show: false, a: "", b: "" }, cmpRows: [] }; },
  async mounted() { await this.load(); },
  methods: {
    purposeBadge(r) { return r.purpose === "sample_check" ? '<span class="badge amber">样本复算</span>' : '<span class="badge teal">历史回测</span>'; },
    statusBadge(s) { return s === "SUCCESS" ? '<span class="badge green">SUCCESS</span>' : '<span class="badge red">' + esc(s) + "</span>"; },
    async load() {
      try { this.runs = await api("/api/runs"); } catch (e) { toast(e.message, "err"); }
    },
    async delRun(r) {
      if (!confirm("确认删除运行 " + r.runId + "?数据库记录与导出文件将一并删除。")) return;
      try {
        await api("/api/runs/" + r.runId, { method: "DELETE" });
        toast("已删除 " + r.runId, "ok");
        this.load();
      } catch (e) { toast(e.message, "err"); }
    },
    async compare() {
      const checked = Array.from(document.querySelectorAll(".cmp:checked")).map(c => c.value);
      if (checked.length !== 2) { toast("请恰好勾选两条运行记录", "err"); return; }
      try {
        const [eqA, eqB] = await Promise.all(checked.map(id => api("/api/runs/" + id + "/equity")));
        const [metA, metB] = await Promise.all(checked.map(id => api("/api/runs/" + id + "/metrics")));
        this.cmp = { show: true, a: checked[0], b: checked[1] };
        this.$nextTick(() => {
          renderLineChart(this.$refs.cmpChart, {
            series: [
              { name: checked[0] + "(策略)", color: "#0f766e", points: eqA.filter(e => e.seriesId === "strategy").map(e => ({ label: e.tradeDate.slice(5), value: e.nav })) },
              { name: checked[1] + "(策略)", color: "#d97706", points: eqB.filter(e => e.seriesId === "strategy").map(e => ({ label: e.tradeDate.slice(5), value: e.nav })) }
            ],
            height: 300, yFmt: v => v.toFixed(3)
          });
        });
        const byKey = m => {
          const o = {};
          m.filter(x => x.seriesId === "strategy").forEach(x => o[x.metric] = x.metricValue);
          return o;
        };
        const ka = byKey(metA), kb = byKey(metB);
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
      } catch (e) { toast(e.message, "err"); }
    },
    aiContext() {
      return "【运行历史页】共 " + this.runs.length + " 次运行:\n" + this.runs.slice(0, 8).map(r =>
        "- " + r.runId + " " + r.purpose + " " + (r.startDate || "") + "~" + (r.endDate || "") + " " + r.status
        + (r.note ? "(" + r.note.slice(0, 40) + ")" : "")).join("\n");
    }
  }
};

/* ================= 根组件 + 哈希路由 ================= */
const ROUTES = {
  overview: { view: OverviewView, title: "概览", sub: "课题 T03.01 相对动量轮动 · 数据/引擎/样本复算状态一览" },
  data: { view: DataPage, title: "数据管理", sub: "内置东方财富行情抓取(日频 · 前复权),可添加任意 ETF 代码,数据落盘可复现" },
  factors: { view: FactorsPage, title: "因子库", sub: "A 阶段《因子清单》22 列规范 · 因子表版本 v1 · 定义来源可查" },
  strategy: { view: StrategyPage, title: "策略配置", sub: "etf_mom_rotation · 12 项规则参数化,可另存版本并导出 B_主策略.json" },
  backtest: { view: BacktestPage, title: "回测运行", sub: "历史回测(真实数据)与 B 样本复算(虚构数据)独立运行,各有 run_id" },
  result: { view: ResultPage, title: "回测结果", sub: "净值对比 · 指标 · 持仓 · 交易解释 · 六件套导出" },
  history: { view: HistoryPage, title: "运行历史", sub: "每次运行(含失败与附加实验)独立 run_id,可勾选对比" }
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
      modal: MODAL,
      ai: { open: false, configured: true, loading: false, input: "", msgs: [],
        permission: true, pos: null, dragging: false, dragOff: { x: 0, y: 0 }, pending: null }
    };
  },
  computed: {
    navItems() {
      return [
        { name: "overview", icon: "◧", label: "概览" },
        { name: "data", icon: "▤", label: "数据管理" },
        { name: "factors", icon: "ƒ", label: "因子库" },
        { name: "strategy", icon: "⚙", label: "策略配置" },
        { name: "backtest", icon: "▶", label: "回测运行" },
        { name: "history", icon: "≡", label: "运行历史" }
      ];
    },
    currentView() { return ROUTES[this.route.name].view; },
    currentTitle() { return ROUTES[this.route.name].title; },
    currentSub() { return ROUTES[this.route.name].sub; },
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
    window.addEventListener("hashchange", () => { this.route = parseHash(); });
    api("/api/ai/configured").then(r => { this.ai.configured = !!r.configured; }).catch(() => {});
    if (parseHash().params.ai === "1") this.ai.open = true;   // #/overview?ai=1 直接打开面板(演示用)
    // 确认卡片按钮事件委托(v-html 内按钮无法直接绑定)
    document.addEventListener("click", e => {
      const b = e.target.closest("[data-ai-act]");
      if (!b) return;
      if (b.dataset.aiAct === "confirm") this.confirmAi();
      else if (b.dataset.aiAct === "cancel") this.cancelAi();
      else if (b.dataset.aiAct === "dl") {
        const a = document.createElement("a");
        a.href = b.dataset.aiUrl;
        a.download = "";
        a.click();
      }
    });
  },
  methods: {
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
        return "权限未开启:未执行「" + name + "」(点击面板右上角 🔓 开启)";
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
        this.ai.msgs.push({ role: "ai", html: '<span class="muted">⚠ ' + esc(e.message) + "</span>" });
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
        this.ai.msgs.push({ role: "ai", html: "🔧 <b>已执行</b> · " + mdLite(String(out).slice(0, 400)) });
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
        this.ai.msgs.push({ role: "ai", html: "🧮 <b>服务端计算完成</b> · " + esc(TOOL_LABELS[t.name] || t.name)
          + "<span class='muted'>(数值由 Java 业务层产生)</span>" });
      }
      // 报告生成完毕提供下载
      for (const t of bc) {
        if (t.name === "generate_report") {
          const br = (res.backendResults || []).find(r => String(r.content || "").indexOf("/api/runs/") >= 0);
          const m = br ? String(br.content).match(/\/api\/runs\/[\w]+\/export\/report\.md/) : null;
          if (m) {
            this.ai.msgs.push({ role: "ai",
              html: "<button class='btn sm' data-ai-act='dl' data-ai-url='" + m[0] + "'>⬇ 下载 report.md</button>"
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
      return "<div class='ai-confirm'><b>🔐 操作确认</b> —— AI 请求执行以下操作:<div style='margin:8px 0'>"
        + rows + "</div><div class='small muted' style='margin-bottom:8px'>持久化/联网操作需你确认;确认后继续,取消则告知 AI 未执行。</div>"
        + "<button class='btn sm' data-ai-act='confirm'>✓ 确认执行</button> "
        + "<button class='btn sm danger' data-ai-act='cancel'>✕ 取消</button></div>";
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
        this.ai.msgs.push({ role: "ai", html: '<span class="muted">⚠ ' + esc(e.message) + "</span>" });
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
        this.ai.msgs.push({ role: "ai", html: '<span class="muted">⚠ ' + esc(e.message) + "</span>" });
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
app.mount("#app");
