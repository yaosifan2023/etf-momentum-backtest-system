======================================================================
ETF 动量轮动:因子 / 策略 / 历史回测与交互展示系统(课程作业 C)v2.1
======================================================================

提交单元 ID、成员姓名与分工:
    个人作业 · 姚思凡(独立完成因子搜集、策略设计、系统实现与回测分析)

主题大类/小类编码、研究问题:
    T03 轮动 / T03.01 相对动量轮动
    研究问题:ETF 池内过去 W 日相对动量是否延续?扣除切换成本后,
    动量轮动相对单资产买入持有的收益、回撤与换手有何差异?
    防御开关(最强动量转负时转现金/国债ETF)能否降低回撤、损失多少上涨机会?

本次阶段、版本、相对上次的修改:
    C 阶段 v2:前端升级为 Vue 3 单页应用(视觉重构);后端按
    控制层/业务层/数据层分层(controller / service+impl / dao);
    新增任意 ETF 代码联网抓取;新增右下角 AI 分析助手(智谱 GLM)。

文件索引:
    pom.xml                    Maven 配置(Spring Boot 3.3.5 / JDK 17 / MyBatis)
    maven-settings.xml         阿里云 Maven 镜像
    启动系统.bat                一键启动(自动使用内置 JDK/Maven,构建并打开浏览器)
    .jdk\ .maven\              项目内置 JDK17 与 Maven(免系统安装)
    src/main/java/com/etfmomentum/
      controller/              控制层(REST API,含 AI 代理入口)
      service/ + service/impl/ 业务层(接口 + 实现)
      dao/                     数据层(MyBatis Mapper 接口)
      entity/                  实体层(10 张表)
      dto/                     传输对象(请求体)
      core/                    回测引擎与因子计算(纯 Java,可独立测试)
      common/ config/          通用异常 / 初始化配置
    src/main/resources/
      application.yml          H2 文件库(默认)/ MyBatis / AI 配置
      application-mysql.yml    MySQL 切换配置
      schema-h2.sql / schema-mysql.sql   建表脚本(启动自动执行)
      mapper/*.xml             MyBatis SQL 映射(显式 SQL + 批量插入)
      data/b_sample.csv        B 阶段虚构教学样本(DEMO_A,8 条日线)
      static/                  Vue 3 前端(index.html SPA + js/spa.js,
                               本地引入 vue.global.prod.js,无需 Node 构建,离线可用)
    src/test/java/             单元测试(样本复算 / 因子 / 指标口径,15 项)
    config/zhipu-key.txt       智谱 AI 密钥(本地私有,提交作业前务必删除!)
    data/                      运行时数据:行情 CSV + H2 数据库
    results/                   每次运行七件套导出(results/{run_id}/)

因子表版本:v1(见 A_因子搜集.xlsx)
实际引用的因子编号:g01_mom_n(主)、g01_ma_ratio(过滤/样本复算)、g01_vol_d(展示)
策略编号与版本:etf_mom_rotation / 1.0.0(与 B_策略说明.pdf 首页一致)

本阶段实际引用的资料、数据或代码及使用条件:
    - 因子定义:Qlib 官方 loader.py(MIT);TA-Lib 文档;Jegadeesh & Titman (1993)
    - 行情数据:东方财富 push2his 接口(日频、前复权),系统内置抓取,无需账号
    - 框架:Spring Boot 3.3.5、MyBatis 3.5、Vue 3.4(本地文件引入)
    - AI 助手:智谱 GLM(glm-5.3),仅服务端调用,密钥不出后端

AI 辅助的用途与人工核对内容:
    AI 辅助生成系统代码骨架、前端页面与配置;人工核对了因子公式与指南 A
    示例一致(均线比值 0.866667)、B 样本复算全部数值(8320 份、手续费
    99.88992/91.47424 元、期末现金 91392.95584 元、首日净值 0.9985019008)、
    净值/回撤/换手指标口径,并端到端运行验证(构建 15/15 测试通过、
    真实数据 2.2 万条抓取、回测/导出/AI 问答均实测)。

已完成内容和仍存在的问题:
    已完成:分层架构、行情抓取(候选池一键 + 任意代码添加)、CSV 导入、
    因子库(22 列规范+实时计算)、策略参数化(可导出 B_主策略.json)、
    B 样本复算(预期值对照)、真实历史回测(T 日信号/T+1 开盘成交/滑点
    手续费)、净值对比、持仓、交易解释、指标统计、双运行对比、七件套导出、
    Vue 3 SPA 界面、AI 分析助手。
    存在问题:日线无法重建盘中成交,以"次日开盘价±滑点"简化;前复权口径
    意味着因子分析与模拟成交共用同一价格序列;未做 Walk-Forward 参数选择
    (参数为预先固定的教学约定,run.json 已说明);AI 回答仅供分析参考。

【数据编号与版本】
    dataset_id=eastmoney_daily_kline;data_version=fqt1_<最新数据日期>;
    资产池:510300/510500/512100/159915/513100/518880/511010(512690 为演示
    "添加代码抓取"功能时加入,可在数据管理页扩展);DEMO_A 为虚构教学数据。

【运行环境】
    JDK 17 + Maven 3.9:项目 .jdk\ 与 .maven\ 已内置,启动脚本自动使用;
    持久层 MyBatis + H2 文件库(默认,免安装)/ MySQL(可选);
    前端 Vue 3 本地文件引入,无需 Node/npm。

【数据准备】
    方式一:启动系统 → 数据管理 → 「一键抓取全部候选池」(需联网);
    方式二:数据管理 → 输入 6 位代码「添加并抓取」任意 ETF;
    方式三:导入 CSV(code,date,open,high,low,close,volume,amount)。

【安装与启动命令】
    双击 启动系统.bat;或 IDEA 打开运行 EtfMomentumApplication;
    或 mvn -s maven-settings.xml spring-boot:run。访问 http://localhost:8080
    单元测试:mvn -s maven-settings.xml test(15 项,含样本复算口径校验)

【AI 分析助手(可拖动 · 可执行操作)】
    右下角 🤖 打开;窗口可按住头部拖动;右上角 🔓/🔒 控制操作权限。
    结合当前页面数据问答,并可通过白名单工具替用户执行:
    修改策略表单参数、设置回测日期、保存并运行回测、运行样本复算、
    抓取行情、添加ETF、切换页面、查询运行详情(工具由前端执行,可一键锁定)。
    密钥保护:密钥仅存于服务端,优先级 application.yml > 环境变量
    ZHIPU_API_KEY > config/zhipu-key.txt;前端与页面源码中不出现密钥。
    *** 提交/打包作业前请删除 config/zhipu-key.txt(或换成空文件)***

【演示步骤】
    1. 概览页查看状态 → 回测运行页「运行样本复算」:12 项对照全打勾;
    2. 数据管理页「一键抓取」+ 输入代码添加一只新 ETF;
    3. 回测运行页选策略、区间 → 运行 → 结果页看净值对比/指标/持仓;
    4. 交易记录页点击任一成交 → 「为什么发生这笔交易」解释弹窗;
    5. 结果页「导出全部(ZIP)」得七件套;「零成本重跑」做附加对照,
       到运行历史页勾选两条对比;
    6. 右下角 AI 助手:在结果页问「分析这次回测的表现与风险」。

【结果目录】
    results/{run_id}/:run.json、config.json、metrics.csv、equity.csv、
    positions.csv、trades.csv、decisions.csv;results/{run_id}.zip 打包下载。

【数据库】
    默认 H2 文件库 ./data/db/(启动自动建表+初始化种子数据);
    MySQL:spring.profiles.active=mysql,建库 etf_momentum(utf8mb4)。

======================================================================
