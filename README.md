# ETF 动量轮动:因子 · 策略 · 回测与交互展示系统

> 课程作业 C 阶段交付系统 · 课题 **T03.01 相对动量轮动**
> Spring Boot 3.3 + MyBatis + Vue 3 + H2/MySQL + 智谱 GLM AI 助手

一个**完整可运行**的量化回测 Web 系统:内置行情抓取、因子库、策略参数化配置、真实历史回测引擎、净值/持仓/交易全过程可视化、六件套结果导出,以及一个能**分析页面数据、替你执行操作**的 AI 助手。

---

## 目录

- [功能总览](#功能总览)
- [技术栈与架构](#技术栈与架构)
- [快速开始](#快速开始)
- [使用指南](#使用指南)
- [回测方法与口径](#回测方法与口径)
- [REST API 文档](#rest-api-文档)
- [AI 分析助手](#ai-分析助手)
- [数据来源与口径](#数据来源与口径)
- [正确性验证](#正确性验证)
- [项目结构](#项目结构)
- [常见问题](#常见问题)

---

## 功能总览

| 模块 | 说明 |
|---|---|
| 📊 **数据管理** | 内置东方财富行情抓取(日频·前复权),一键更新候选池;**输入 6 位代码即可添加任意 ETF**(自动识别沪/深市场、回填名称);支持 CSV 导入兜底;抓取日志实时显示;数据落盘 `data/{code}.csv` 可复现 |
| ƒ **因子库** | 按 A 阶段《因子清单》22 列规范管理因子定义(公式/含义/参数/缺失值处理/定义来源,来源含 Qlib、TA-Lib、Jegadeesh & Titman 1993);因子实时计算预览与回测引擎共用同一实现 |
| ⚙️ **策略配置** | 12 项规则全部参数化(动量窗口 W、持仓数 K、调仓频率、防御开关、趋势过滤、费率滑点等);同名保存自动递增版本;一键导出 `B_主策略.json` |
| ▶ **回测运行** | T 日收盘信号 → T+1 开盘成交(无未来信息);支持零成本对照、参数覆盖等附加实验;B 样本复算一键验证引擎正确性 |
| 📈 **结果展示** | 净值曲线(悬停查数,策略 vs 基准)、8 项指标对照(含口径说明)、持仓快照、信号决策;**点击任一成交弹出"这笔交易为什么发生"**(当日动量排名/目标权重/实际执行) |
| 📦 **结果导出** | 每次运行七件套:`run.json` / `config.json` / `metrics.csv` / `equity.csv` / `positions.csv` / `trades.csv` / `decisions.csv`,按 run_id 归档 + ZIP 打包 |
| ⇄ **运行历史** | 全部运行可追溯(含失败记录);勾选任意两次运行做净值叠加与指标差值对比 |
| 🤖 **AI 分析助手** | 右下角可拖动聊天面板(智谱 GLM);能结合页面数据分析,还能通过白名单工具**替你执行操作**(改参数/跑回测/抓数据),带权限开关 |

## 技术栈与架构

| 层 | 技术 |
|---|---|
| 前端 | Vue 3(本地文件引入,无 CDN 依赖,离线可用)+ 原生 SVG 图表 + REST/JSON |
| 控制层 | Spring Boot 3.3.5 `@RestController`(8 个控制器) |
| 业务层 | service 接口 + `service.impl` 实现(行情/因子/回测/导出/AI) |
| 数据层 | MyBatis 3.5(Mapper 接口 + XML 显式 SQL,含 foreach 批量插入) |
| 数据库 | H2 文件库(默认,零安装)/ MySQL(profile 一键切换);10 张表,`schema-{platform}.sql` 启动自动建表 |
| 领域引擎 | `core/` 纯 Java(因子计算 + 回测引擎),可脱离框架做 JUnit 单测 |
| AI | 智谱 GLM(glm-5.3),后端代理转发 + function calling 工具协议 |

```
Vue 3 SPA ──REST──▶ controller ──▶ service(接口/impl) ──▶ dao(MyBatis) ──▶ H2/MySQL
                        │                 │
                        │                 └──▶ core/(FactorMath · RotationBacktest · 指标装配)
                        └──▶ AiServiceImpl ──HTTPS──▶ 智谱 GLM(工具调用回环)
```

## 快速开始

### 方式一:命令行(推荐)

要求:JDK 17+、Maven 3.6+(国内网络建议配置阿里云镜像,仓库自带 `maven-settings.xml`)

```bash
# 构建并运行(Windows 双击 启动系统.bat 等效)
mvn -s maven-settings.xml spring-boot:run
# 或
mvn -s maven-settings.xml package
java -jar target/etf-momentum-system-1.0.0.jar
```

浏览器打开 **http://localhost:8080**(首次启动自动建表并初始化种子数据:ETF 候选池、3 个因子定义、默认策略、DEMO 教学样本)。

### 方式二:IDEA

打开项目 → SDK 选 17 → 运行 `EtfMomentumApplication`。

### 数据库切换

默认 H2 文件库(`data/db/`,零配置)。切换 MySQL:

```sql
CREATE DATABASE etf_momentum DEFAULT CHARACTER SET utf8mb4;
```

```bash
java -jar app.jar --spring.profiles.active=mysql   # 账号密码见 application-mysql.yml
```

### 使用前置:抓取行情

进入「数据管理」→「一键抓取全部候选池」(需联网);或输入代码添加任意 ETF(如 `512690`、`515790`)。首次抓取后即可运行真实回测。

## 使用指南

1. **概览**:数据/样本复算状态一览,快速入口
2. **数据管理**:抓取行情、添加 ETF、导入 CSV、预览走势
3. **因子库**:查看 22 列因子定义;实时计算预览(与引擎同源)
4. **策略配置**:调参 → 实时规则预览 → 保存新版本 → 导出 B_主策略.json
5. **回测运行**:选策略 + 区间 → 运行(可勾选零成本对照/参数覆盖);或运行 B 样本复算
6. **结果页**:净值对比 → 指标 → 交易/信号/持仓页签 → 点击交易看解释 → 导出 ZIP
7. **运行历史**:两次运行勾选对比;删除运行(连带结果文件)

## 回测方法与口径

| 口径 | 约定 |
|---|---|
| 信号→成交 | T 日 15:05 收盘数据可用后产生信号,T+1 日 09:30 按开盘价成交;末日信号不执行 |
| 成本 | 双边手续费按成交金额(可配最低手续费);滑点计入成交价(买价上浮/卖价下调),不重复扣费 |
| 净值 | `nav = 账户权益 ÷ 评价起点交易前权益`;初始净值 1 参与历史峰值与回撤计算 |
| 年化 | 252 交易日,简单收益口径;夏普无风险利率取 0(教学假设) |
| 换手率 | Σ成交金额 ÷ 平均权益 ÷ 年数 |
| 入选/退出 | 动量降序取前 K(并列按代码升序);落选次日开盘卖出;**已持有且仍在目标内不调仓(缓冲)** |
| 防御 | 最强动量低于阈值或候选池为空 → 转现金(或防御资产) |
| 指标 NA | 样本不足/无法计算记 NA,`definition` 列写明口径 |
| 参数诚实性 | 参数为预先固定的教学约定,未做选择区间优化(run.json `split` 字段如实标注) |
| 可追溯 | 每次运行独立 run_id;config.json 保存实际配置快照;同参数重跑结果确定一致 |

## REST API 文档

### 概览与数据

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/overview` | 概览统计(资产数/行情条数/覆盖区间/运行数/样本复算状态) |
| GET | `/api/etfs` | ETF 列表(含覆盖区间、条数、来源) |
| GET | `/api/etfs/{code}/bars?limit=60` | 最近 N 条日行情 |
| POST | `/api/data/refresh/{code}` | 抓取/更新单只 ETF 全部日线 |
| POST | `/api/data/refresh-all` | 一键抓取全部候选池 |
| POST | `/api/data/add-etf` `{code}` | 添加任意 ETF 并抓取(自动判市场) |
| POST | `/api/data/import-csv` (multipart file) | 导入 CSV(`code,date,open,high,low,close,volume,amount`) |

### 因子与策略

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/factors` | 因子定义列表(22 列) |
| GET | `/api/factors/{factorId}/values?code=&window=&limit=` | 因子实时计算 |
| GET/POST/DELETE | `/api/strategies[/{id}]` | 策略配置增删查(POST 保存新版本) |
| GET | `/api/strategies/{id}/b-json` | 生成 B_主策略.json(12 字段) |

### 回测与运行

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/backtest/run` | 运行历史回测(body:`configId/start/end/zeroCost/note/override*`) |
| POST | `/api/backtest/sample-check` | B 样本复算(12 项预期值对照) |
| GET | `/api/runs` | 运行历史 |
| GET | `/api/runs/{runId}` | 运行详情(含解析后的 config) |
| GET | `/api/runs/{runId}/metrics·equity·positions?date·trades·decisions` | 结果数据 |
| GET | `/api/runs/{runId}/explain?date=YYYY-MM-DD` | 某信号日决策明细 + 次日实际成交(交易解释) |
| DELETE | `/api/runs/{runId}` | 删除运行(数据库 + 结果文件) |
| GET | `/api/runs/{runId}/export/{file}` | 下载单件(config.json/run.json/各 csv) |
| GET | `/api/runs/{runId}/export-all` | 打包 ZIP |

### AI

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ai/configured` | 密钥是否已配置 |
| POST | `/api/ai/chat` | 问答(body:`question/context/history/tools`);返回 `{answer}` 或 `{pending:true, toolCalls:[...]}` |
| POST | `/api/ai/chat` | 工具结果回传(body 追加 `toolCalls/toolResults`)→ 最终回答 |

## AI 分析助手

- **形态**:右下角 🤖 悬浮球 → 可拖动聊天面板;🔒/🔓 一键锁定/放开操作权限
- **分析**:自动携带当前页面数据(回测指标/交易/因子值)作为上下文
- **执行**(智谱 function calling,前端白名单工具):

| 工具 | 作用 |
|---|---|
| `update_strategy_form` | 修改策略表单参数(窗口/持仓/频率/防御/费率…) |
| `set_backtest_dates` | 设置回测起止日期 |
| `save_and_run_backtest` | 保存当前表单为新版本并立即运行回测 |
| `run_sample_check` | 运行 B 样本复算 |
| `refresh_market_data` / `add_etf` | 抓取行情 / 添加 ETF |
| `navigate` / `get_run_detail` | 切换页面 / 查询运行指标 |

- **密钥安全**:密钥只存服务端,前端与页面源码不出现;优先级 `application.yml` > 环境变量 `ZHIPU_API_KEY` > `config/zhipu-key.txt`(该文件已在 `.gitignore` 中,**永远不会上传**)。配置方法:

```bash
# 方式一:环境变量
set ZHIPU_API_KEY=你的key

# 方式二:本地文件(项目根目录)
echo 你的key > config/zhipu-key.txt
```

## 数据来源与口径

- **接口**:东方财富 `push2his.eastmoney.com/api/qt/stock/kline/get`(日频 `klt=101`,前复权 `fqt=1`),无需账号
- **字段顺序**:日期,开盘,收盘,最高,最低,成交量(手),成交额(元)
- **默认候选池**:510300 沪深300ETF(基准)/ 510500 / 512100 / 159915 / 513100 纳指 / 518880 黄金 / 511010 国债(防御)
- **缺失处理**:停牌/缺失日无记录;因子窗口不足或含无效价 → 该资产当期不参与排名(NA)
- **已知简化**:前复权口径下因子分析与模拟成交共用同一价格序列(日线接口限制,报告已说明)

## 正确性验证

- **单元测试 15/15**(`mvn test`):因子计算、指标口径、B 样本复算
- **B 样本复算 12/12**:与课程指南 6.6 手工演算逐位一致 —— 买入 8320 份 @12.006、手续费 99.88992/91.47424 元、期末现金 91392.95584、首日净值 0.9985019008、恰好等于阈值的边界退出等
- **实测参考**(2019-01 ~ 2026-11,W=20/K=2/周调仓/防御转现金):策略年化 **13.18%**、最大回撤 **−23.43%**,基准(510300 买入持有)年化 6.72%、回撤 −44.75%;零成本对照净值 3.83 vs 2.56(成本拖累显著)

## 项目结构

```
etf-momentum-system/
├── pom.xml / maven-settings.xml / 启动系统.bat
├── src/main/java/com/etfmomentum/
│   ├── controller/          # 控制层(8 个 REST 控制器)
│   ├── service/ + service/impl/   # 业务层(接口 + 实现)
│   ├── dao/                 # 数据层(MyBatis Mapper)
│   ├── entity/              # 实体层(10 张表)
│   ├── dto/                 # 请求传输对象
│   ├── core/                # 回测引擎与因子计算(纯 Java)
│   ├── common/ config/      # 异常处理 / 数据初始化
│   └── EtfMomentumApplication.java
├── src/main/resources/
│   ├── application.yml      # H2(默认)/ MyBatis / AI 配置
│   ├── application-mysql.yml
│   ├── schema-h2.sql / schema-mysql.sql
│   ├── mapper/*.xml         # MyBatis SQL
│   ├── data/b_sample.csv    # B 样本虚构教学数据(DEMO_A)
│   └── static/              # Vue 3 SPA(index.html + js/spa.js + vendor/vue.global.prod.js)
├── src/test/java/           # 15 项单元测试
├── 系统介绍与汇报.md          # 汇报材料
└── .gitignore               # 含密钥目录 config/,绝不入库
```

## 常见问题

- **双击 bat 后页面打不开?** 等构建完成(首次 2-5 分钟);若报"8080 被占用/jar 被锁",关闭上一次的黑窗口再试
- **AI 提示未配置密钥?** 按上文配置 `ZHIPU_API_KEY` 或 `config/zhipu-key.txt` 后重启
- **想彻底重置?** 停止应用后删除 `data/`(和 `results/`)再启动
- **行情抓取失败?** 需要能访问 `push2his.eastmoney.com`;或用 CSV 导入
- **本仓库不含 JDK/Maven/密钥/运行数据**:JDK 17 与 Maven 请自行安装(或用 IDEA 下载),密钥按上文配置,行情数据启动后一键抓取

---

> 本系统为课程作业交付物,回测结果为历史样本内表现,不构成投资建议。
