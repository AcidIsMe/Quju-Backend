CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL UNIQUE,
    avatar_url VARCHAR(500),
    gender VARCHAR(10),
    birthday DATE,
    bio VARCHAR(200),
    interest_tags TEXT DEFAULT '[]',
    role VARCHAR(20) NOT NULL DEFAULT 'personal',
    status VARCHAR(30) NOT NULL DEFAULT 'pending_activation',
    credit_score INT NOT NULL DEFAULT 100,
    location_lat DECIMAL(10,7),
    location_lng DECIMAL(10,7),
    location_updated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS activation_tokens (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    token VARCHAR(600) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS login_attempts (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    success BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS merchant_profiles (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    merchant_name VARCHAR(100) NOT NULL,
    merchant_nickname VARCHAR(50) UNIQUE,
    activity_domains TEXT,
    license_image_url VARCHAR(500),
    audit_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    audit_reason TEXT,
    audited_by VARCHAR(36),
    audited_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS teams (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    interest_tags TEXT,
    join_type VARCHAR(10) NOT NULL DEFAULT 'public',
    max_members INT,
    current_members INT NOT NULL DEFAULT 1,
    leader_id VARCHAR(36) NOT NULL,
    avatar_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS activities (
    id VARCHAR(36) PRIMARY KEY,
    creator_id VARCHAR(36) NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    tags TEXT,
    activity_type VARCHAR(50),
    cover_image_url VARCHAR(500),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    registration_deadline TIMESTAMP NOT NULL,
    max_participants INT NOT NULL,
    current_participants INT NOT NULL DEFAULT 0,
    min_credit_score INT NOT NULL DEFAULT 0,
    min_age INT NOT NULL DEFAULT 0,
    fee_type VARCHAR(20) NOT NULL DEFAULT 'free',
    fee_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    city VARCHAR(100),
    location_name VARCHAR(200),
    location_lat DECIMAL(10,7),
    location_lng DECIMAL(10,7),
    status VARCHAR(30) NOT NULL DEFAULT 'draft',
    ai_review_result VARCHAR(50),
    review_reason TEXT,
    reviewed_by VARCHAR(36),
    reviewed_at TIMESTAMP,
    is_team_activity BOOLEAN NOT NULL DEFAULT FALSE,
    team_id VARCHAR(36),
    cloned_from_id VARCHAR(36),
    check_in_qr_code VARCHAR(500),
    check_in_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    check_in_location_required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS activity_templates (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT,
    tags TEXT,
    activity_type VARCHAR(50),
    preset_duration_minutes INT,
    preset_max_participants INT,
    is_system BOOLEAN NOT NULL DEFAULT TRUE,
    creator_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS registrations (
    id VARCHAR(36) PRIMARY KEY,
    activity_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'registered',
    form_data TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP,
    checked_in_at TIMESTAMP,
    UNIQUE (activity_id, user_id)
);

CREATE TABLE IF NOT EXISTS waitlist (
    id VARCHAR(36) PRIMARY KEY,
    activity_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    position INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'waiting',
    notified_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (activity_id, user_id)
);

CREATE TABLE IF NOT EXISTS reviews (
    id VARCHAR(36) PRIMARY KEY,
    activity_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (activity_id, user_id)
);

CREATE TABLE IF NOT EXISTS friendships (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    friend_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    action_user_id VARCHAR(36) NOT NULL,
    remark_name VARCHAR(30),
    group_tags TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, friend_id)
);

CREATE TABLE IF NOT EXISTS follows (
    follower_id VARCHAR(36) NOT NULL,
    followed_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, followed_id)
);

CREATE TABLE IF NOT EXISTS team_members (
    id VARCHAR(36) PRIMARY KEY,
    team_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    role VARCHAR(10) NOT NULL DEFAULT 'member',
    points INT NOT NULL DEFAULT 0,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (team_id, user_id)
);

CREATE TABLE IF NOT EXISTS team_join_requests (
    id VARCHAR(36) PRIMARY KEY,
    team_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'pending',
    reviewed_by VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS team_blacklist (
    team_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (team_id, user_id)
);

CREATE TABLE IF NOT EXISTS activity_summaries (
    id VARCHAR(36) PRIMARY KEY,
    activity_id VARCHAR(36) NOT NULL UNIQUE,
    content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS summary_images (
    id VARCHAR(36) PRIMARY KEY,
    summary_id VARCHAR(36) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    category VARCHAR(20) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_bans (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    reason TEXT NOT NULL,
    banned_by VARCHAR(36) NOT NULL,
    banned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    revoked_by VARCHAR(36),
    revoked_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notifications (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS im_messages (
    id VARCHAR(36) PRIMARY KEY,
    entity_type VARCHAR(30) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    sender_id VARCHAR(36) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'text',
    content TEXT,
    metadata TEXT,
    recalled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recalled_at TIMESTAMP,
    read_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS group_chat_read_markers (
    id VARCHAR(36) PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    last_read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (group_id, user_id)
);

ALTER TABLE IF EXISTS im_messages ALTER COLUMN entity_id VARCHAR(100) NOT NULL;

-- 插入系统活动模板
MERGE INTO activity_templates (id, name, category, description, tags, activity_type, preset_duration_minutes, preset_max_participants, is_system) KEY(id)
VALUES ('tpl_hiking', '户外徒步', '户外徒步', '一场轻松的户外徒步活动', '["户外","徒步","周末"]', '户外徒步', 240, 30, TRUE);

MERGE INTO activity_templates (id, name, category, description, tags, activity_type, preset_duration_minutes, preset_max_participants, is_system) KEY(id)
VALUES ('tpl_board_game', '桌游聚会', '桌游聚会', '适合破冰和新朋友交流的桌游局', '["桌游","聚会"]', '桌游聚会', 180, 12, TRUE);
