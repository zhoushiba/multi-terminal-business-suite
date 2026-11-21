-- =====================================================================
-- 数字内容与数据服务业务中台 —— MySQL 8 种子数据
-- 全部使用 INSERT IGNORE，重复执行不会报错也不会产生重复数据
-- 默认账号：admin/admin123、editor/editor123、operator/operator123
-- =====================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO sys_role (id, code, name, remark, sort) VALUES
    (1, 'admin', '超级管理员', '拥有全部权限，前端菜单也全量下发', 1),
    (2, 'editor', '内容运营', '负责内容创建、审核与发布', 2),
    (3, 'operator', '活动运营', '负责活动、场次与报名核销', 3),
    (4, 'member', '普通会员', '小程序端登录用户', 4);

INSERT IGNORE INTO sys_user (id, username, password, nickname, phone, status, source) VALUES
    (1, 'admin', 'pbkdf2$120000$7061696e756f2d73756974652d736565$fe97bbc9b67339ff998bffb964dfa8c40a8955f5b814705c06467f4767f79209', '系统管理员', '13800000001', 1, 'web'),
    (2, 'editor', 'pbkdf2$120000$7061696e756f2d73756974652d736565$5a70ad9c657b9ffa18fc184e1b02bf17b7cf0ef3e7f18dc6b459c00928dd08b5', '内容编辑小李', '13800000002', 1, 'web'),
    (3, 'operator', 'pbkdf2$120000$7061696e756f2d73756974652d736565$34130d6f35a65a6b98d240ebcf65a4d0a2f015abd02ff28d119fe2b58c63b81f', '活动运营小王', '13800000003', 1, 'web');

INSERT IGNORE INTO sys_user_role (id, user_id, role_id) VALUES (1, 1, 1), (2, 2, 2), (3, 3, 3);

INSERT IGNORE INTO sys_menu (id, parent_id, name, path, component, icon, type, perm, sort, hidden) VALUES
    (1,  0,  '数据看板', '/dashboard',        'dashboard/index',   'Odometer',   1, 'stats:view',     1, 0),
    (2,  0,  '内容中心', '/content',          NULL,                'Document',   0, NULL,             2, 0),
    (3,  2,  '内容管理', '/content/list',     'content/index',     'Tickets',    1, 'content:list',   1, 0),
    (4,  2,  '分类管理', '/content/category', 'content/category',  'Files',      1, 'content:write',  2, 0),
    (5,  0,  '资源点位', '/geo',              NULL,                'MapLocation', 0, NULL,            3, 0),
    (6,  5,  '点位管理', '/geo/points',       'geo/index',         'MapLocation', 1, 'geo:list',      1, 0),
    (7,  0,  '活动运营', '/activity',         NULL,                'Calendar',   0, NULL,             4, 0),
    (8,  7,  '活动管理', '/activity/list',    'activity/index',    'Calendar',   1, 'activity:list',  1, 0),
    (9,  7,  '报名核销', '/activity/signup',  'activity/signup',   'Ticket',     1, 'signup:list',    2, 0),
    (10, 0,  '系统管理', '/system',          NULL,                'Setting',    0, NULL,             5, 0),
    (11, 10, '用户管理', '/system/user',      'system/user/index', 'User',       1, 'user:list',      1, 0),
    (12, 10, '角色权限', '/system/role',      'system/role/index', 'Lock',       1, 'role:list',      2, 0),
    (13, 0,  'AI 助手',  '/ai',               'ai/index',          'MagicStick', 1, 'ai:use',         6, 0),
    (14, 3,  '内容编辑', NULL, NULL, NULL, 2, 'content:write',  1, 0),
    (15, 3,  '内容审核', NULL, NULL, NULL, 2, 'content:review', 2, 0),
    (16, 6,  '点位编辑', NULL, NULL, NULL, 2, 'geo:write',      1, 0),
    (17, 8,  '活动编辑', NULL, NULL, NULL, 2, 'activity:write', 1, 0),
    (18, 9,  '报名核销', NULL, NULL, NULL, 2, 'signup:verify',  1, 0),
    (19, 11, '用户编辑', NULL, NULL, NULL, 2, 'user:write',     1, 0),
    (20, 12, '角色编辑', NULL, NULL, NULL, 2, 'role:write',     1, 0),
    (21, 3,  '文件上传', NULL, NULL, NULL, 2, 'upload:write',   3, 0);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
    (1, 1, 1), (2, 1, 2), (3, 1, 3), (4, 1, 4), (5, 1, 5), (6, 1, 6), (7, 1, 7), (8, 1, 8),
    (9, 1, 9), (10, 1, 10), (11, 1, 11), (12, 1, 12), (13, 1, 13), (14, 1, 14), (15, 1, 15),
    (16, 1, 16), (17, 1, 17), (18, 1, 18), (19, 1, 19), (20, 1, 20), (21, 1, 21),
    (22, 2, 1), (23, 2, 2), (24, 2, 3), (25, 2, 4), (26, 2, 14), (27, 2, 15), (28, 2, 21),
    (29, 3, 1), (30, 3, 5), (31, 3, 6), (32, 3, 7), (33, 3, 8), (34, 3, 9), (35, 3, 16),
    (36, 3, 17), (37, 3, 18);

INSERT IGNORE INTO cms_category (id, name, code, parent_id, sort, status) VALUES
    (1, '文旅资讯', 'travel-news', 0, 1, 1),
    (2, '遥感数据服务', 'remote-sensing', 0, 2, 1),
    (3, '活动公告', 'notice', 0, 3, 1),
    (4, '精选攻略', 'guide', 0, 4, 1);

INSERT IGNORE INTO cms_tag (id, name, code) VALUES
    (1, '贵州', 'guizhou'), (2, '文旅', 'culture-tourism'), (3, '遥感', 'remote-sensing'),
    (4, '活动', 'event'), (5, '攻略', 'guide');

INSERT IGNORE INTO cms_content (id, title, summary, body, cover_url, category_id, content_type, status, top_flag, sort, author_id, author_name, publish_time, view_count) VALUES
    (1, '贵州秋季文旅线路发布：喀斯特峰林与苗寨夜游', '省文旅厅发布五条秋季精品线路，覆盖喀斯特峰林、苗寨夜游与温泉康养。', '<p>本次发布的线路以"喀斯特 + 民族文化"为主线，串联黄果树、荔波小七孔与西江千户苗寨。</p>', '/covers/autumn-route.jpg', 1, 'article', 2, 1, 100, 2, '内容编辑小李', NOW() - INTERVAL 13 DAY, 1820),
    (2, '遥感影像解译：都柳江流域耕地变化监测季报', '基于高分影像的地块级变化检测，识别新增耕地与建设占用共 126 处。', '<p>本季报使用 2026 年三季度高分二号影像，对都柳江流域开展地块级变化检测。</p>', '/covers/remote-sensing-q3.jpg', 2, 'article', 2, 0, 90, 2, '内容编辑小李', NOW() - INTERVAL 12 DAY, 940),
    (3, '西江千户苗寨秋季民俗活动全攻略', '从观景台机位到长桌宴时间，一篇讲清苗寨两日行程怎么排。', '<p>上午建议先上观景台，顺光拍摄吊脚楼群；下午可参与非遗蜡染体验。</p>', '/covers/miao-village.jpg', 4, 'gallery', 2, 1, 95, 2, '内容编辑小李', NOW() - INTERVAL 11 DAY, 2310),
    (4, '高标准农田遥感核查成果通过省级验收', '核查面积 12.6 万亩，地块边界与台账一致率 98.7%。', '<p>核查采用"影像判读 + 外业抽检"双轨制，抽检比例 5%。</p>', '/covers/farmland-check.jpg', 2, 'article', 2, 0, 80, 2, '内容编辑小李', NOW() - INTERVAL 10 DAY, 760),
    (5, '梵净山预约须知：分时段入园与索道时刻', '每日限流 8000 人，需提前一日在小程序预约。', '<p>索道 7:30 开始运行，建议预约 8:00-9:00 时段以避开人流。</p>', '/covers/fanjingshan.jpg', 3, 'article', 2, 0, 70, 2, '内容编辑小李', NOW() - INTERVAL 9 DAY, 3160),
    (6, '黄果树瀑布水量最佳的三个时段', '结合上游电站放水规律，给出三个水量最饱满的拍摄时段。', '<p>9:00-10:30 顺光且水量稳定，是出片率最高的时段。</p>', '/covers/huangguoshu.jpg', 4, 'video', 2, 0, 60, 2, '内容编辑小李', NOW() - INTERVAL 8 DAY, 2740),
    (7, '卫星遥感助力茶园种植结构摸底', '通过 NDVI 时序曲线区分茶园、林地与撂荒地，准确率 94.2%。', '<p>时序曲线比单期影像更可靠，尤其是在多云雾的山区。</p>', '/covers/tea-garden.jpg', 2, 'article', 2, 0, 50, 2, '内容编辑小李', NOW() - INTERVAL 6 DAY, 620),
    (8, '青岩古镇冬日慢游路线', '避开正午人流，从北门进、南门出，两小时走完核心街区。', '<p>推荐从北门进入，先逛背街，再登城墙俯瞰。</p>', '/covers/qingyan.jpg', 1, 'article', 2, 0, 40, 2, '内容编辑小李', NOW() - INTERVAL 5 DAY, 1580),
    (9, '荔波小七孔徒步装备清单', '涉水路段较多，鞋与防水包是重点。', '<p>景区内多为石板路与亲水步道，建议穿防滑溯溪鞋。</p>', '/covers/xiaoqikong.jpg', 4, 'article', 2, 0, 30, 2, '内容编辑小李', NOW() - INTERVAL 4 DAY, 1120),
    (10, '遵义会议会址红色研学预约开放', '研学团队需提前三个工作日提交申请。', '<p>预约通过小程序提交，团队人数上限 50 人。</p>', '/covers/zunyi.jpg', 3, 'article', 2, 0, 20, 2, '内容编辑小李', NOW() - INTERVAL 3 DAY, 890),
    (11, '冬季温泉线路推荐', '整理五处温泉与周边住宿。', '<p>待补充泉质检测数据。</p>', '/covers/hot-spring.jpg', 1, 'article', 1, 0, 10, 2, '内容编辑小李', NULL, 0),
    (12, '遥感数据服务价格说明', '按面积与分辨率分档计价，草稿待定稿。', '<p>草稿：价格表待财务确认。</p>', NULL, 2, 'article', 0, 0, 0, 2, '内容编辑小李', NULL, 0),
    (13, '旧版活动公告（已下架）', '历史公告，保留用于审计。', '<p>内容已过期。</p>', NULL, 3, 'article', 3, 0, 0, 2, '内容编辑小李', NOW() - INTERVAL 7 DAY, 45),
    (14, '内容审核规范（待审）', '面向编辑团队的内容合规 checklist。', '<p>待审核：敏感词、图片版权、数据来源。</p>', NULL, 3, 'article', 1, 0, 0, 2, '内容编辑小李', NULL, 0);

INSERT IGNORE INTO cms_content_tag (id, content_id, tag_id) VALUES
    (1, 1, 1), (2, 1, 2), (3, 2, 3), (4, 3, 1), (5, 3, 5), (6, 4, 3), (7, 5, 1), (8, 6, 5),
    (9, 7, 3), (10, 8, 5), (11, 9, 5), (12, 10, 2), (13, 10, 4), (14, 11, 2);

INSERT IGNORE INTO geo_region (id, name, code, parent_id, level, center_lng, center_lat) VALUES
    (1, '贵州省', 'gz', 0, 1, 106.6302, 26.6477),
    (2, '贵阳市', 'gz-guiyang', 1, 2, 106.6302, 26.6477),
    (3, '黔东南苗族侗族自治州', 'gz-qiandongnan', 1, 2, 107.9775, 26.5834),
    (4, '遵义市', 'gz-zunyi', 1, 2, 106.9275, 27.7256),
    (5, '安顺市', 'gz-anshun', 1, 2, 105.9476, 26.2537),
    (6, '铜仁市', 'gz-tongren', 1, 2, 109.1916, 27.7183),
    (7, '黔南布依族苗族自治州', 'gz-qiannan', 1, 2, 107.5172, 26.2585);

INSERT IGNORE INTO geo_point (id, name, region_id, type, lng, lat, address, cover_url, summary, status) VALUES
    (1, '黄果树瀑布', 5, 'scenic', 105.6670, 25.9960, '安顺市镇宁县黄果树镇', '/covers/p-huangguoshu.jpg', '亚洲第一大瀑布，秋季水量充沛。', 1),
    (2, '西江千户苗寨', 3, 'scenic', 108.1730, 26.4940, '黔东南州雷山县西江镇', '/covers/p-xijiang.jpg', '世界最大苗族聚居村寨，夜景尤佳。', 1),
    (3, '荔波小七孔', 7, 'scenic', 107.7160, 25.2590, '黔南州荔波县', '/covers/p-xiaoqikong.jpg', '喀斯特水上森林，夏季最佳。', 1),
    (4, '梵净山', 6, 'scenic', 108.6960, 27.9100, '铜仁市江口县', '/covers/p-fanjingshan.jpg', '世界自然遗产，需预约入园。', 1),
    (5, '青岩古镇', 2, 'heritage', 106.6870, 26.3310, '贵阳市花溪区青岩镇', '/covers/p-qingyan.jpg', '明代军事古镇，石巷与牌坊保存完好。', 1),
    (6, '遵义会议会址', 4, 'heritage', 106.9270, 27.7250, '遵义市红花岗区', '/covers/p-zunyi.jpg', '红色研学核心点位。', 1),
    (7, '遥感地块 A-高标准农田', 2, 'plot', 106.7120, 26.5980, '贵阳市白云区', NULL, '2026 年高标准农田核查地块，面积 1280 亩。', 1),
    (8, '遥感地块 B-生态茶园', 3, 'plot', 107.4460, 26.7060, '黔东南州麻江县', NULL, '茶园种植结构摸底样本区，面积 640 亩。', 1),
    (9, '黄果树游客服务中心', 5, 'service', 105.6700, 25.9990, '安顺市镇宁县', NULL, '提供咨询、寄存与摆渡车换乘。', 1),
    (10, '西江观景台停车场', 3, 'service', 108.1690, 26.4980, '黔东南州雷山县', NULL, '可停 300 辆，夜间 22 点关闭。', 1);

INSERT IGNORE INTO activity (id, title, cover_url, region_id, description, status, signup_start_time, signup_end_time, start_time, end_time) VALUES
    (1, '秋日喀斯特徒步大会', '/covers/a-hike.jpg', 5, '<p>黄果树—天星桥环线徒步，全程 12 公里，含专业领队与补给。</p>', 1,
     CURDATE() - INTERVAL 7 DAY, CURDATE() + INTERVAL 18 DAY, CURDATE() + INTERVAL 20 DAY + INTERVAL 8 HOUR, CURDATE() + INTERVAL 20 DAY + INTERVAL 17 HOUR),
    (2, '苗寨非遗手作体验', '/covers/a-craft.jpg', 3, '<p>蜡染与银饰体验，含材料包与非遗传承人现场指导。</p>', 1,
     CURDATE() - INTERVAL 7 DAY, CURDATE() + INTERVAL 26 DAY, CURDATE() + INTERVAL 27 DAY + INTERVAL 14 HOUR, CURDATE() + INTERVAL 27 DAY + INTERVAL 16 HOUR),
    (3, '遥感开放日：走进影像处理中心', '/covers/a-open-day.jpg', 2, '<p>参观影像处理中心，讲解变化检测流程与成果应用。</p>', 1,
     CURDATE() - INTERVAL 7 DAY, CURDATE() + INTERVAL 34 DAY, CURDATE() + INTERVAL 35 DAY + INTERVAL 10 HOUR, CURDATE() + INTERVAL 35 DAY + INTERVAL 12 HOUR);

INSERT IGNORE INTO activity_session (id, activity_id, name, start_time, end_time, capacity, remain, price, version) VALUES
    (101, 1, '上午场 08:30-12:00', CURDATE() + INTERVAL 20 DAY + INTERVAL 8 HOUR, CURDATE() + INTERVAL 20 DAY + INTERVAL 12 HOUR, 30, 28, 0.00, 0),
    (102, 1, '下午场 14:00-17:30', CURDATE() + INTERVAL 20 DAY + INTERVAL 14 HOUR, CURDATE() + INTERVAL 20 DAY + INTERVAL 17 HOUR, 30, 30, 0.00, 0),
    (103, 2, '周末手作场 14:00-16:00', CURDATE() + INTERVAL 27 DAY + INTERVAL 14 HOUR, CURDATE() + INTERVAL 27 DAY + INTERVAL 16 HOUR, 12, 11, 68.00, 0),
    (104, 3, '开放日 10:00-12:00', CURDATE() + INTERVAL 35 DAY + INTERVAL 10 HOUR, CURDATE() + INTERVAL 35 DAY + INTERVAL 12 HOUR, 20, 20, 0.00, 0);

INSERT IGNORE INTO activity_signup (id, session_id, activity_id, user_id, contact_name, contact_phone, code, status, signup_time, verify_time, verify_by) VALUES
    (1, 101, 1, 2, '李编辑', '13800000002', 'SG2026000000A1', 1, NOW() - INTERVAL 3 DAY, NULL, NULL),
    (2, 101, 1, 3, '王运营', '13800000003', 'SG2026000000A2', 1, NOW() - INTERVAL 2 DAY, NULL, NULL),
    (3, 103, 2, 2, '李编辑', '13800000002', 'SG2026000000B1', 2, NOW() - INTERVAL 1 DAY, NOW(), 3);

INSERT IGNORE INTO signup_verify_log (id, signup_id, operator_id, action, result, remark) VALUES
    (1, 3, 3, 'verify', 'success', '现场核销，材料包已发放');

INSERT IGNORE INTO ai_conversation (id, user_id, title, scene, message_count) VALUES
    (1, 1, '如何写黄果树秋季线路的摘要？', 'assistant', 2);

INSERT IGNORE INTO ai_message (id, conversation_id, msg_role, content, provider, model, prompt_tokens, completion_tokens, latency_ms) VALUES
    (1, 1, 'user', '帮我给「黄果树瀑布水量最佳的三个时段」写一段 80 字摘要。', NULL, NULL, NULL, NULL, NULL),
    (2, 1, 'assistant', '结合上游放水规律与光照方向，9:00-10:30 顺光且水量稳定，是拍摄与观景的最佳窗口；午后逆光但水量回升，适合观看水雾。建议避开正午团队高峰。', 'mock', 'mock-extractive-v1', 42, 68, 3);

-- ---------------------------------------------------------------------
-- 系统管理 / 系统配置（菜单 + 授权）：只挂给超级管理员
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu (id, parent_id, name, path, component, icon, type, perm, sort, hidden) VALUES
    (22, 10, '系统配置', '/system/config', 'system/config', 'Setting', 1, NULL, 3, 0);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES (38, 1, 22);

-- ---------------------------------------------------------------------
-- 操作日志（审计）演示数据：让后台「系统配置 → 操作日志」分页有内容可看
-- 真实写入侧由 OperationLogAspect 统一织入（见 docs/roadmap.md）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO operation_log (id, trace_id, user_id, username, action, method, path, ip, cost_ms, success, detail, created_at) VALUES
    (1, 'tr-20261118-9f2a1c', 2, 'editor',   '登录后台',     'POST', '/api/auth/login',             '127.0.0.1', 96, 1, '账号密码登录成功',                   NOW() - INTERVAL 52 HOUR),
    (2, 'tr-20261118-a71c3e', 2, 'editor',   '发布内容',     'POST', '/api/contents/1/publish',     '127.0.0.1', 41, 1, '内容《贵州秋季文旅线路发布》发布成功', NOW() - INTERVAL 48 HOUR),
    (3, 'tr-20261119-2b8d07', 2, 'editor',   '提交审核',     'POST', '/api/contents/11/submit',     '127.0.0.1', 18, 1, '草稿转为待审核',                     NOW() - INTERVAL 30 HOUR),
    (4, 'tr-20261119-5c93fa', 3, 'operator', '创建活动场次', 'POST', '/api/activities/2/sessions',  '127.0.0.1', 55, 1, '周末手作场，容量 12 人',             NOW() - INTERVAL 26 HOUR),
    (5, 'tr-20261120-7e41b2', 3, 'operator', '核销报名',     'POST', '/api/signups/verify',         '127.0.0.1', 33, 1, '核销码 SG2026000000B1 核销成功',      NOW() - INTERVAL 18 HOUR),
    (6, 'tr-20261120-9a0d5c', 3, 'operator', '核销报名',     'POST', '/api/signups/verify',         '127.0.0.1', 12, 0, '核销码不存在或已核销',               NOW() - INTERVAL 17 HOUR),
    (7, 'tr-20261121-c3f6e1', 1, 'admin',    '重置用户密码', 'POST', '/api/users/3/reset-password', '127.0.0.1', 74, 1, '运营账号密码已重置',                 NOW() - INTERVAL 6 HOUR),
    (8, 'tr-20261121-e8b274', 1, 'admin',    '更新角色权限', 'PUT',  '/api/roles/2',                '127.0.0.1', 63, 1, '内容运营角色菜单授权已全量重写',     NOW() - INTERVAL 2 HOUR);
