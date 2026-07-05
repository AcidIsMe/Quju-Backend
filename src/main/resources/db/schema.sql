CREATE DATABASE IF NOT EXISTS quju_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE quju_platform;
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL UNIQUE,
    avatar_url VARCHAR(500),
    gender VARCHAR(10),
    birthday DATE,
    bio VARCHAR(200),
    interest_tags JSON,
    role VARCHAR(20) NOT NULL DEFAULT 'personal',
    status VARCHAR(30) NOT NULL DEFAULT 'pending_activation',
    credit_score INT NOT NULL DEFAULT 100,
    location_lat DECIMAL(10,7),
    location_lng DECIMAL(10,7),
    location_updated_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_users_status (status),
    INDEX idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS activation_tokens (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at DATETIME(3) NOT NULL,
    used_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_activation_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    token VARCHAR(600) NOT NULL UNIQUE,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_refresh_user_revoked (user_id, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS login_attempts (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    success BOOLEAN NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_login_email_created (email, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS merchant_profiles (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    merchant_name VARCHAR(100) NOT NULL,
    merchant_nickname VARCHAR(50) UNIQUE,
    activity_domains JSON,
    license_image_url VARCHAR(500),
    audit_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    audit_reason TEXT,
    audited_by VARCHAR(36),
    audited_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_merchant_audit_status (audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS teams (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    interest_tags JSON,
    join_type VARCHAR(10) NOT NULL DEFAULT 'public',
    max_members INT,
    current_members INT NOT NULL DEFAULT 1,
    leader_id VARCHAR(36) NOT NULL,
    avatar_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_teams_status (status),
    INDEX idx_teams_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS activities (
    id VARCHAR(36) PRIMARY KEY,
    creator_id VARCHAR(36) NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    tags JSON,
    activity_type VARCHAR(50),
    cover_image_url VARCHAR(500),
    start_time DATETIME(3) NOT NULL,
    end_time DATETIME(3) NOT NULL,
    registration_deadline DATETIME(3) NOT NULL,
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
    reviewed_at DATETIME(3),
    is_team_activity BOOLEAN NOT NULL DEFAULT FALSE,
    team_id VARCHAR(36),
    cloned_from_id VARCHAR(36),
    check_in_qr_code VARCHAR(500),
    check_in_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    check_in_location_required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_activities_creator (creator_id),
    INDEX idx_activities_status (status),
    INDEX idx_activities_type (activity_type),
    INDEX idx_activities_city (city),
    INDEX idx_activities_fee_type (fee_type),
    INDEX idx_activities_start_time (start_time),
    INDEX idx_activities_created_at (created_at),
    INDEX idx_activities_location (location_lat, location_lng),
    FULLTEXT INDEX ft_activities_title_desc (title, description)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS activity_templates (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT,
    tags JSON,
    activity_type VARCHAR(50),
    preset_duration_minutes INT,
    preset_max_participants INT,
    is_system BOOLEAN NOT NULL DEFAULT TRUE,
    creator_id VARCHAR(36),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_templates_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS registrations (
    id VARCHAR(36) PRIMARY KEY,
    activity_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'registered',
    form_data JSON,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    cancelled_at DATETIME(3),
    checked_in_at DATETIME(3),
    UNIQUE KEY uk_registration_activity_user (activity_id, user_id),
    INDEX idx_registration_activity_status (activity_id, status),
    INDEX idx_registration_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS waitlist (
    id VARCHAR(36) PRIMARY KEY,
    activity_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    position INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'waiting',
    notified_at DATETIME(3),
    expires_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_waitlist_activity_user (activity_id, user_id),
    INDEX idx_waitlist_activity_status_position (activity_id, status, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reviews (
    id VARCHAR(36) PRIMARY KEY,
    activity_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    rating TINYINT UNSIGNED DEFAULT NULL COMMENT '评分 1-5',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_review_activity_user (activity_id, user_id),
    INDEX idx_reviews_activity_created (activity_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friendships (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    friend_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    action_user_id VARCHAR(36) NOT NULL,
    remark_name VARCHAR(30),
    group_tags JSON,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_friendships_user_friend (user_id, friend_id),
    INDEX idx_friendships_friend_status (friend_id, status),
    INDEX idx_friendships_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS follows (
    follower_id VARCHAR(36) NOT NULL,
    followed_id VARCHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (follower_id, followed_id),
    INDEX idx_follows_followed (followed_id),
    INDEX idx_follows_follower (follower_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_members (
    id VARCHAR(36) PRIMARY KEY,
    team_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    role VARCHAR(10) NOT NULL DEFAULT 'member',
    points INT NOT NULL DEFAULT 0,
    joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_team_members_team_user (team_id, user_id),
    INDEX idx_team_members_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_join_requests (
    id VARCHAR(36) PRIMARY KEY,
    team_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'pending',
    reviewed_by VARCHAR(36),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_team_join_requests_team_status (team_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_blacklist (
    team_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (team_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_announcements (
    id VARCHAR(36) PRIMARY KEY,
    team_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_team_announcements_team (team_id, pinned DESC, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_albums (
    id VARCHAR(36) PRIMARY KEY,
    team_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    cover_image_url VARCHAR(500),
    photo_count INT NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_team_albums_team (team_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_photos (
    id VARCHAR(36) PRIMARY KEY,
    album_id VARCHAR(36) NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500),
    description VARCHAR(200),
    uploaded_by VARCHAR(36) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_team_photos_album (album_id, sort_order, created_at DESC),
    INDEX idx_team_photos_team (team_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 小队投票
CREATE TABLE IF NOT EXISTS team_polls (
    id VARCHAR(36) PRIMARY KEY,
    team_id VARCHAR(36) NOT NULL,
    title VARCHAR(200) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    closed_at DATETIME(3),
    INDEX idx_team_polls_team (team_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS poll_options (
    id VARCHAR(36) PRIMARY KEY,
    poll_id VARCHAR(36) NOT NULL,
    option_text VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_poll_options_poll (poll_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS poll_votes (
    id VARCHAR(36) PRIMARY KEY,
    poll_id VARCHAR(36) NOT NULL,
    option_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_poll_vote (poll_id, user_id),
    INDEX idx_poll_votes_option (option_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS activity_summaries (
    id VARCHAR(36) PRIMARY KEY,
    activity_id VARCHAR(36) NOT NULL UNIQUE,
    content TEXT,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS summary_images (
    id VARCHAR(36) PRIMARY KEY,
    summary_id VARCHAR(36) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    category VARCHAR(20) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_summary_images_summary_category (summary_id, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_bans (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    reason TEXT NOT NULL,
    banned_by VARCHAR(36) NOT NULL,
    banned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at DATETIME(3),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    revoked_by VARCHAR(36),
    revoked_at DATETIME(3),
    INDEX idx_user_bans_user_active (user_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSON,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_notifications_user_read_created (user_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS im_messages (
    id VARCHAR(36) PRIMARY KEY,
    entity_type VARCHAR(30) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    sender_id VARCHAR(36) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'text',
    content TEXT,
    metadata JSON,
    recalled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    recalled_at DATETIME(3),
    read_at DATETIME(3),
    INDEX idx_im_messages_entity_created (entity_type, entity_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS group_chat_read_markers (
    id VARCHAR(36) PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    last_read_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_group_read_marker (group_id, user_id),
    INDEX idx_group_read_marker_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 扩展 entity_id 字段长度以支持私聊 "uuid:uuid" 格式
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'im_messages' AND column_name = 'entity_id' AND character_maximum_length < 100) > 0,
    'ALTER TABLE im_messages MODIFY COLUMN entity_id VARCHAR(100) NOT NULL',
    'SELECT ''entity_id already sufficient'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 确保 im_messages 表有 read_at 列（私聊已读标记）
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'im_messages' AND column_name = 'read_at') = 0,
    'ALTER TABLE im_messages ADD COLUMN read_at DATETIME(3) AFTER recalled_at',
    'SELECT ''read_at already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO activity_templates (id, name, category, description, tags, activity_type, preset_duration_minutes, preset_max_participants, is_system)
SELECT 'tpl_hiking', '户外徒步', '户外徒步', '一场轻松的户外徒步活动', JSON_ARRAY('户外','徒步','周末'), '户外徒步', 240, 30, TRUE
WHERE NOT EXISTS (SELECT 1 FROM activity_templates WHERE id = 'tpl_hiking');

INSERT INTO activity_templates (id, name, category, description, tags, activity_type, preset_duration_minutes, preset_max_participants, is_system)
SELECT 'tpl_board_game', '桌游聚会', '桌游聚会', '适合破冰和新朋友交流的桌游局', JSON_ARRAY('桌游','聚会'), '桌游聚会', 180, 12, TRUE
WHERE NOT EXISTS (SELECT 1 FROM activity_templates WHERE id = 'tpl_board_game');

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'activities' AND column_name = 'min_age') = 0,
    'ALTER TABLE activities ADD COLUMN min_age INT NOT NULL DEFAULT 0 AFTER min_credit_score',
    'SELECT ''min_age already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'activities' AND column_name = 'fee_type') = 0,
    'ALTER TABLE activities ADD COLUMN fee_type VARCHAR(20) NOT NULL DEFAULT ''free'' AFTER min_age',
    'SELECT ''fee_type already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'activities' AND column_name = 'fee_amount') = 0,
    'ALTER TABLE activities ADD COLUMN fee_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER fee_type',
    'SELECT ''fee_amount already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'activities' AND column_name = 'city') = 0,
    'ALTER TABLE activities ADD COLUMN city VARCHAR(100) AFTER fee_amount',
    'SELECT ''city already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'activities' AND index_name = 'idx_activities_city') = 0,
    'CREATE INDEX idx_activities_city ON activities (city)',
    'SELECT ''idx_activities_city already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'activities' AND index_name = 'idx_activities_fee_type') = 0,
    'CREATE INDEX idx_activities_fee_type ON activities (fee_type)',
    'SELECT ''idx_activities_fee_type already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 迭代2：team_members 增加积分字段
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'team_members' AND column_name = 'points') = 0,
    'ALTER TABLE team_members ADD COLUMN points INT NOT NULL DEFAULT 0 AFTER role',
    'SELECT ''points already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
