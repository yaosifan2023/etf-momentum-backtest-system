-- MySQL 建表脚本(可重复执行)
CREATE TABLE IF NOT EXISTS etf_info (
  code         VARCHAR(16) PRIMARY KEY,
  name         VARCHAR(64),
  market       VARCHAR(8),
  secid        VARCHAR(16),
  category     VARCHAR(32),
  defensive    TINYINT(1),
  benchmark    TINYINT(1),
  enabled      TINYINT(1),
  source       VARCHAR(16),
  latest_date  DATE,
  bar_count    INT,
  note         VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS daily_bar (
  id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  code       VARCHAR(16) NOT NULL,
  trade_date DATE       NOT NULL,
  open  DOUBLE, high DOUBLE, low DOUBLE, close DOUBLE,
  volume DOUBLE, amount DOUBLE,
  UNIQUE KEY uk_bar_code_date (code, trade_date),
  KEY idx_bar_code_date (code, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS factor_def (
  factor_id       VARCHAR(32) PRIMARY KEY,
  factor_name     VARCHAR(128),
  factor_category VARCHAR(32),
  scope           TEXT, meaning TEXT, formula TEXT, required_data TEXT,
  input_freq      VARCHAR(128), calc_freq VARCHAR(128),
  calc_params     TEXT, min_history TEXT, output_desc TEXT, direction TEXT,
  data_caliber    TEXT, missing_policy TEXT, outlier_policy TEXT,
  standardization TEXT, neutralization TEXT, availability TEXT,
  source_name     VARCHAR(255), source_url VARCHAR(512), source_detail TEXT,
  candidate_source TEXT, limits TEXT,
  table_version   VARCHAR(8)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS strategy_config (
  id                 BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  strategy_code      VARCHAR(64),
  name               VARCHAR(128),
  version            INT,
  momentum_window    INT,
  holding_count      INT,
  rebalance_freq     VARCHAR(16),
  defensive_enabled  TINYINT(1),
  defensive_threshold DOUBLE,
  defensive_mode     VARCHAR(8),
  defensive_asset    VARCHAR(16),
  trend_filter_enabled TINYINT(1),
  trend_window       INT,
  trend_threshold    DOUBLE,
  commission_rate    DOUBLE,
  minimum_commission DOUBLE,
  slippage_rate      DOUBLE,
  initial_cash       DOUBLE,
  benchmark_asset    VARCHAR(16),
  universe_note      TEXT,
  note               TEXT,
  params_json        TEXT,
  created_at         DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS backtest_run (
  run_id             VARCHAR(48) PRIMARY KEY,
  purpose            VARCHAR(32),
  status             VARCHAR(16),
  data_type          VARCHAR(16),
  strategy_code      VARCHAR(64),
  strategy_version   VARCHAR(16),
  strategy_config_id BIGINT,
  config_json        TEXT,
  start_date         DATE,
  end_date           DATE,
  initial_cash       DOUBLE,
  benchmark          VARCHAR(64),
  message            TEXT,
  note               TEXT,
  created_at         DATETIME,
  finished_at        DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS equity_point (
  id             BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  run_id         VARCHAR(48),
  series_id      VARCHAR(16),
  trade_date     DATE,
  nav DOUBLE, cash DOUBLE, position_value DOUBLE,
  KEY idx_eq_run (run_id, series_id, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS position_snapshot (
  id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  run_id     VARCHAR(48),
  series_id  VARCHAR(16),
  trade_date DATE,
  asset_id   VARCHAR(16),
  quantity   DOUBLE,
  weight     DOUBLE,
  KEY idx_pos_run (run_id, series_id, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trade_record (
  id             BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  run_id         VARCHAR(48),
  series_id      VARCHAR(16),
  signal_time    VARCHAR(40),
  execution_time VARCHAR(40),
  asset_id       VARCHAR(16),
  side           VARCHAR(8),
  quantity  DOUBLE,
  price     DOUBLE,
  fee       DOUBLE,
  slippage_cost DOUBLE,
  status    VARCHAR(16),
  reason    TEXT,
  KEY idx_trd_run (run_id, series_id, execution_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS decision_record (
  id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  run_id              VARCHAR(48),
  signal_date         DATE,
  executed            TINYINT(1),
  defensive_triggered TINYINT(1),
  defensive_reason    VARCHAR(255),
  payload             TEXT,
  KEY idx_dec_run (run_id, signal_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS run_metric (
  id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  run_id       VARCHAR(48),
  series_id    VARCHAR(16),
  metric       VARCHAR(32),
  metric_value DOUBLE,
  unit         VARCHAR(16),
  definition   TEXT,
  KEY idx_met_run (run_id, series_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
