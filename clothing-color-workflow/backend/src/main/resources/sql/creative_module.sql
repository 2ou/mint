CREATE TABLE IF NOT EXISTS creative_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_code VARCHAR(64) UNIQUE,
  project_name VARCHAR(255),
  project_type VARCHAR(32) DEFAULT 'CANVAS',
  status VARCHAR(32) DEFAULT 'DRAFT',
  source_template_id BIGINT NULL,
  current_version_no INT DEFAULT 0,
  current_canvas_json LONGTEXT NULL,
  current_flow_json LONGTEXT NULL,
  current_config_json LONGTEXT NULL,
  cover_url VARCHAR(1000) NULL,
  description VARCHAR(1000) NULL,
  last_run_time DATETIME NULL,
  last_save_time DATETIME NULL,
  remark VARCHAR(1000) NULL,
  deleted TINYINT(1) DEFAULT 0,
  create_by VARCHAR(64) NULL,
  update_by VARCHAR(64) NULL,
  create_time DATETIME,
  update_time DATETIME
);

CREATE TABLE IF NOT EXISTS creative_project_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT,
  version_no INT,
  save_type VARCHAR(32) DEFAULT 'MANUAL',
  canvas_json LONGTEXT,
  flow_json LONGTEXT NULL,
  config_json LONGTEXT NULL,
  summary VARCHAR(1000) NULL,
  is_current TINYINT(1) DEFAULT 0,
  deleted TINYINT(1) DEFAULT 0,
  create_by VARCHAR(64) NULL,
  create_time DATETIME
);

CREATE TABLE IF NOT EXISTS creative_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_code VARCHAR(64) UNIQUE,
  template_name VARCHAR(255),
  template_type VARCHAR(32) DEFAULT 'WORKFLOW',
  category VARCHAR(64) NULL,
  source_project_id BIGINT NULL,
  status VARCHAR(32) DEFAULT 'ENABLED',
  is_system TINYINT(1) DEFAULT 0,
  cover_url VARCHAR(1000) NULL,
  description VARCHAR(1000) NULL,
  canvas_json LONGTEXT,
  flow_json LONGTEXT NULL,
  config_json LONGTEXT NULL,
  use_count INT DEFAULT 0,
  deleted TINYINT(1) DEFAULT 0,
  create_by VARCHAR(64) NULL,
  update_by VARCHAR(64) NULL,
  create_time DATETIME,
  update_time DATETIME
);

CREATE TABLE IF NOT EXISTS creative_asset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  asset_code VARCHAR(64) UNIQUE,
  project_id BIGINT NULL,
  project_version_id BIGINT NULL,
  source_type VARCHAR(32),
  asset_type VARCHAR(32),
  biz_type VARCHAR(32),
  file_name VARCHAR(255) NULL,
  file_ext VARCHAR(32) NULL,
  mime_type VARCHAR(128) NULL,
  file_size BIGINT NULL,
  width INT NULL,
  height INT NULL,
  duration_ms BIGINT NULL,
  source_url VARCHAR(1000) NULL,
  oss_url VARCHAR(1000) NULL,
  thumbnail_url VARCHAR(1000) NULL,
  metadata_json LONGTEXT NULL,
  deleted TINYINT(1) DEFAULT 0,
  create_by VARCHAR(64) NULL,
  create_time DATETIME
);

CREATE TABLE IF NOT EXISTS creative_node_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_code VARCHAR(64) UNIQUE,
  project_id BIGINT,
  project_version_id BIGINT NULL,
  node_id VARCHAR(128),
  node_name VARCHAR(255) NULL,
  node_type VARCHAR(64),
  provider VARCHAR(32) NULL,
  model_code VARCHAR(128) NULL,
  run_mode VARCHAR(32) NULL,
  status VARCHAR(32) DEFAULT 'INIT',
  input_json LONGTEXT NULL,
  request_json LONGTEXT NULL,
  output_json LONGTEXT NULL,
  selected_output_asset_id BIGINT NULL,
  error_code VARCHAR(128) NULL,
  error_msg VARCHAR(1000) NULL,
  start_time DATETIME NULL,
  end_time DATETIME NULL,
  deleted TINYINT(1) DEFAULT 0,
  create_by VARCHAR(64) NULL,
  create_time DATETIME,
  update_time DATETIME
);

CREATE TABLE IF NOT EXISTS creative_async_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_code VARCHAR(64) UNIQUE,
  project_id BIGINT,
  project_version_id BIGINT NULL,
  node_run_id BIGINT NULL,
  task_type VARCHAR(32),
  provider VARCHAR(32),
  model_code VARCHAR(128),
  provider_task_id VARCHAR(128) NULL,
  callback_url VARCHAR(500) NULL,
  status VARCHAR(32) DEFAULT 'INIT',
  request_json LONGTEXT NULL,
  provider_response_json LONGTEXT NULL,
  result_json LONGTEXT NULL,
  result_url VARCHAR(1000) NULL,
  final_asset_id BIGINT NULL,
  fail_code VARCHAR(128) NULL,
  fail_msg VARCHAR(1000) NULL,
  retry_count INT DEFAULT 0,
  callback_count INT DEFAULT 0,
  last_query_time DATETIME NULL,
  next_retry_time DATETIME NULL,
  start_time DATETIME NULL,
  finish_time DATETIME NULL,
  deleted TINYINT(1) DEFAULT 0,
  create_by VARCHAR(64) NULL,
  create_time DATETIME,
  update_time DATETIME
);

CREATE TABLE IF NOT EXISTS creative_async_task_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT,
  log_type VARCHAR(32),
  content LONGTEXT NULL,
  create_time DATETIME
);

CREATE TABLE IF NOT EXISTS creative_project_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT,
  action_type VARCHAR(32),
  content VARCHAR(1000) NULL,
  operator VARCHAR(64) NULL,
  create_time DATETIME
);
