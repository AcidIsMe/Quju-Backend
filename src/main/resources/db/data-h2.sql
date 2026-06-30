-- Development seed data for Web/admin integration with the h2 profile.
-- Admin login: admin@quju.test / Admin123456

MERGE INTO users (id, email, password_hash, nickname, avatar_url, role, status, credit_score, interest_tags, created_at, updated_at) KEY(id)
VALUES ('admin-user', 'admin@quju.test', '$2a$10$9EsbpUsxx7VcaY5iQHHToOaRdaMbnfQreUOvBArzxS3pQhoETfYoy', '平台管理员', '', 'admin', 'active', 100, '["管理"]', TIMESTAMP '2026-06-01 09:00:00', TIMESTAMP '2026-06-01 09:00:00');

MERGE INTO users (id, email, password_hash, nickname, avatar_url, role, status, credit_score, interest_tags, created_at, updated_at) KEY(id)
VALUES ('creator-user', 'creator@quju.test', '$2a$10$9EsbpUsxx7VcaY5iQHHToOaRdaMbnfQreUOvBArzxS3pQhoETfYoy', '山野同频', '', 'personal', 'active', 96, '["徒步","摄影"]', TIMESTAMP '2026-06-02 09:00:00', TIMESTAMP '2026-06-02 09:00:00');

MERGE INTO users (id, email, password_hash, nickname, avatar_url, role, status, credit_score, interest_tags, created_at, updated_at) KEY(id)
VALUES ('board-user', 'board@quju.test', '$2a$10$9EsbpUsxx7VcaY5iQHHToOaRdaMbnfQreUOvBArzxS3pQhoETfYoy', '小桌游局', '', 'personal', 'active', 92, '["桌游","聚会"]', TIMESTAMP '2026-06-03 09:00:00', TIMESTAMP '2026-06-03 09:00:00');

MERGE INTO users (id, email, password_hash, nickname, avatar_url, role, status, credit_score, interest_tags, created_at, updated_at) KEY(id)
VALUES ('merchant-user', 'merchant@quju.test', '$2a$10$9EsbpUsxx7VcaY5iQHHToOaRdaMbnfQreUOvBArzxS3pQhoETfYoy', '城市活动商家', '', 'merchant', 'active', 100, '["城市探索"]', TIMESTAMP '2026-06-04 09:00:00', TIMESTAMP '2026-06-04 09:00:00');

MERGE INTO users (id, email, password_hash, nickname, avatar_url, role, status, credit_score, interest_tags, created_at, updated_at) KEY(id)
VALUES ('banned-user', 'banned@quju.test', '$2a$10$9EsbpUsxx7VcaY5iQHHToOaRdaMbnfQreUOvBArzxS3pQhoETfYoy', '待解封用户', '', 'personal', 'banned', 61, '["观影"]', TIMESTAMP '2026-06-05 09:00:00', TIMESTAMP '2026-06-05 09:00:00');

MERGE INTO merchant_profiles (id, user_id, merchant_name, merchant_nickname, activity_domains, license_image_url, audit_status, audit_reason, created_at, updated_at) KEY(id)
VALUES ('merchant-profile-pending', 'merchant-user', '趣聚城市活动社', '城市活动社', '["城市探索","聚餐美食"]', '/uploads/license/demo-license.png', 'pending', NULL, TIMESTAMP '2026-06-10 10:00:00', TIMESTAMP '2026-06-10 10:00:00');

MERGE INTO activities (id, creator_id, title, description, tags, activity_type, cover_image_url, start_time, end_time, registration_deadline, max_participants, current_participants, min_credit_score, min_age, fee_type, fee_amount, city, location_name, location_lat, location_lng, status, created_at, updated_at) KEY(id)
VALUES ('activity-review-1', 'creator-user', '周末香山轻徒步', '适合新手的轻量徒步路线，集合后统一出发。', '["徒步","周末","户外"]', '户外徒步', '', TIMESTAMP '2026-07-04 09:00:00', TIMESTAMP '2026-07-04 13:00:00', TIMESTAMP '2026-07-03 20:00:00', 20, 6, 70, 18, 'free', 0.00, '北京', '香山公园东门', 39.9929000, 116.1883000, 'pending_manual_review', TIMESTAMP '2026-06-20 09:00:00', TIMESTAMP '2026-06-20 09:00:00');

MERGE INTO activities (id, creator_id, title, description, tags, activity_type, cover_image_url, start_time, end_time, registration_deadline, max_participants, current_participants, min_credit_score, min_age, fee_type, fee_amount, city, location_name, location_lat, location_lng, status, created_at, updated_at) KEY(id)
VALUES ('activity-review-2', 'board-user', '狼人杀新手友好局', '面向新玩家的桌游聚会，会有主持人讲解规则。', '["桌游","狼人杀"]', '桌游聚会', '', TIMESTAMP '2026-07-05 19:00:00', TIMESTAMP '2026-07-05 22:00:00', TIMESTAMP '2026-07-05 12:00:00', 12, 4, 60, 18, 'paid', 39.00, '北京', '五道口桌游空间', 39.9921000, 116.3376000, 'pending_manual_review', TIMESTAMP '2026-06-21 09:00:00', TIMESTAMP '2026-06-21 09:00:00');

MERGE INTO activities (id, creator_id, title, description, tags, activity_type, cover_image_url, start_time, end_time, registration_deadline, max_participants, current_participants, min_credit_score, min_age, fee_type, fee_amount, city, location_name, location_lat, location_lng, status, created_at, updated_at) KEY(id)
VALUES ('activity-published-1', 'creator-user', '奥森夜跑 5 公里', '工作日晚间轻松跑，配速友好。', '["运动","夜跑"]', '运动健身', '', TIMESTAMP '2026-07-06 20:00:00', TIMESTAMP '2026-07-06 21:00:00', TIMESTAMP '2026-07-06 18:00:00', 30, 18, 70, 16, 'free', 0.00, '北京', '奥林匹克森林公园南门', 40.0109000, 116.3912000, 'published', TIMESTAMP '2026-06-22 09:00:00', TIMESTAMP '2026-06-22 09:00:00');

MERGE INTO activities (id, creator_id, title, description, tags, activity_type, cover_image_url, start_time, end_time, registration_deadline, max_participants, current_participants, min_credit_score, min_age, fee_type, fee_amount, city, location_name, location_lat, location_lng, status, review_reason, created_at, updated_at) KEY(id)
VALUES ('activity-taken-down-1', 'board-user', '临时下架的观影局', '用于验证后台恢复操作的活动。', '["观影"]', '观影娱乐', '', TIMESTAMP '2026-07-07 19:30:00', TIMESTAMP '2026-07-07 22:00:00', TIMESTAMP '2026-07-07 12:00:00', 10, 3, 70, 18, 'paid', 49.00, '北京', '三里屯影院', 39.9368000, 116.4551000, 'taken_down', '联调演示下架状态', TIMESTAMP '2026-06-23 09:00:00', TIMESTAMP '2026-06-23 09:00:00');

MERGE INTO teams (id, name, description, interest_tags, join_type, max_members, current_members, leader_id, avatar_url, status, created_at, updated_at) KEY(id)
VALUES ('team-outdoor', '北京周末户外小队', '周末徒步、骑行和城市探索。', '["徒步","骑行"]', 'public', 50, 8, 'creator-user', '', 'active', TIMESTAMP '2026-06-12 09:00:00', TIMESTAMP '2026-06-12 09:00:00');
