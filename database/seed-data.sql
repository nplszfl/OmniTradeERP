-- ============================================================
-- 跨境电商 ERP 种子数据
-- 与 init.sql 配套使用，跑在 init.sql 之后
-- ID 区间约定：
--   用户/角色/权限：1-999
--   商品/分类：1000-9999
--   订单/订单项：1-999999
--   库存/仓库：1-999
--   财务流水：1-999999
-- ============================================================

-- ============================================================
-- erp_user 数据库
-- ============================================================
-- ===================================================
-- 字符集必须显式声明：MySQL 官方镜像默认 LANG 为空（POSIX locale），
-- mysql 客户端会退化成 latin1 连接，导致本文件里的 UTF-8 中文被双重编码
-- （UTF-8 字节被当作 latin1 再转 UTF-8），数据库里存成乱码。
-- 每个 init 文件由 docker-entrypoint 用独立的 mysql 会话执行，
-- 所以 SET NAMES 必须写在每个文件内部，容器级 locale 兜底不可靠。
SET NAMES utf8mb4;

USE `erp_user`;

-- 角色
INSERT INTO `t_role` (`id`, `role_code`, `role_name`, `description`, `status`) VALUES
(1, 'SUPER_ADMIN', '超级管理员', '系统最高权限，可访问所有功能', 1),
(2, 'OPS_ADMIN', '运营管理员', '负责日常运营，订单/商品/库存', 1),
(3, 'FINANCE', '财务人员', '查看财务报表、处理结算', 1),
(4, 'WAREHOUSE', '仓库管理员', '负责出入库、库存盘点', 1),
(5, 'CUSTOMER_SERVICE', '客服人员', '处理客户问题与售后', 1);

-- 用户（密码 admin123 用 BCrypt 加密后的样子：$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy）
INSERT INTO `t_user` (`id`, `username`, `password`, `real_name`, `email`, `phone`, `status`, `last_login_time`) VALUES
(1, 'admin',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '系统管理员', 'admin@crossborder.com',   '13800138000', 1, NOW()),
(2, 'ops001',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '张运营',   'ops001@crossborder.com',  '13800138001', 1, NOW()),
(3, 'finance001','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '李财务', 'finance001@crossborder.com','13800138002', 1, NOW()),
(4, 'wh001',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '王仓管',   'wh001@crossborder.com',   '13800138003', 1, NOW()),
(5, 'cs001',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '赵客服',   'cs001@crossborder.com',   '13800138004', 1, NOW()),
(6, 'ops002',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '陈运营',   'ops002@crossborder.com',  '13800138005', 1, NULL),
(7, 'ops003',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '林运营',   'ops003@crossborder.com',  '13800138006', 1, NULL);

-- 用户-角色关联
INSERT INTO `t_user_role` (`id`, `user_id`, `role_id`) VALUES
(1, 1, 1),(2, 2, 2),(3, 3, 3),(4, 4, 4),(5, 5, 5),(6, 6, 2),(7, 7, 2);

-- 权限（树形）
INSERT INTO `t_permission` (`id`, `parent_id`, `permission_code`, `permission_name`, `permission_type`, `url`, `sort_order`, `status`) VALUES
(1,    0,   'DASHBOARD',         '数据看板',     'menu', '/dashboard',           1,  1),
(10,   0,   'ORDER',             '订单管理',     'menu', '/order',               10, 1),
(11,   10,  'ORDER_LIST',        '订单列表',     'menu', '/order',               1,  1),
(12,   10,  'ORDER_DETAIL',      '订单详情',     'menu', '/order/detail',        2,  1),
(20,   0,   'PRODUCT',           '商品管理',     'menu', '/product',             20, 1),
(21,   20,  'PRODUCT_LIST',      '商品列表',     'menu', '/product',             1,  1),
(22,   20,  'PRODUCT_CATEGORY',  '商品分类',     'menu', '/product/category',    2,  1),
(23,   20,  'PRODUCT_MAPPING',   '平台映射',     'menu', '/product/mapping',     3,  1),
(30,   0,   'INVENTORY',         '库存管理',     'menu', '/inventory',           30, 1),
(31,   30,  'INVENTORY_LIST',    '库存列表',     'menu', '/inventory',           1,  1),
(32,   30,  'INVENTORY_ALERT',   '库存预警',     'menu', '/inventory-alert',     2,  1),
(33,   30,  'WAREHOUSE',         '仓库管理',     'menu', '/warehouse',           3,  1),
(34,   30,  'INVENTORY_INOUT',   '出入库记录',   'menu', '/inventory/inout',     4,  1),
(40,   0,   'LOGISTICS',         '物流管理',     'menu', '/logistics',           40, 1),
(50,   0,   'CUSTOMER',          '客户管理',     'menu', '/customer',            50, 1),
(60,   0,   'SUPPLIER',          '供应商管理',   'menu', '/supplier',            60, 1),
(70,   0,   'FINANCE',           '财务管理',     'menu', '/finance',             70, 1),
(80,   0,   'PLATFORM',          '平台配置',     'menu', '/platform',            80, 1),
(90,   0,   'USER_MGMT',         '用户管理',     'menu', '/user',                90, 1),
(100,  0,   'AI_CENTER',         'AI 智能中心',  'menu', '/ai',                  100,1),
(101,  100, 'AI_ASSISTANT',      'AI 客服助手',  'menu', '/ai/assistant',        1,  1),
(102,  100, 'AI_PRICING',        '智能定价',     'menu', '/ai/pricing',          2,  1),
(103,  100, 'AI_RECOMMEND',      '选品推荐',     'menu', '/ai/recommend',        3,  1),
(104,  100, 'AI_ANOMALY',        '异常检测',     'menu', '/ai/anomaly',          4,  1),
(105,  100, 'AI_DESCRIPTION',    '商品描述生成', 'menu', '/ai/description',      5,  1),
(106,  100, 'AI_INVENTORY_PRED', '库存预测',     'menu', '/ai/inventory-predict',6,  1),
(110,  0,   'PURCHASE',          '采购管理',     'menu', '/purchase',            110,1),
(120,  0,   'ANALYTICS',         '数据分析',     'menu', '/analytics',           120,1),
(130,  0,   'REPORT',            '报表中心',     'menu', '/report',              130,1),
(140,  0,   'SOCIAL',            '社交电商',     'menu', '/social',              140,1),
(150,  0,   'TENANT',            '租户管理',     'menu', '/tenant',              150,1);

-- 角色-权限（超级管理员拥有全部，业务经理拥有运营类）
INSERT INTO `t_role_permission` (`id`, `role_id`, `permission_id`) VALUES
-- super admin
(1, 1, 1),(2, 1, 10),(3, 1, 20),(4, 1, 30),(5, 1, 40),(6, 1, 50),(7, 1, 60),(8, 1, 70),(9, 1, 80),(10, 1, 90),
(11, 1, 100),(12, 1, 110),(13, 1, 120),(14, 1, 130),(15, 1, 140),(16, 1, 150),
(20, 1, 11),(21, 1, 12),(22, 1, 21),(23, 1, 22),(24, 1, 23),(25, 1, 31),(26, 1, 32),(27, 1, 33),(28, 1, 34),
(30, 1, 101),(31, 1, 102),(32, 1, 103),(33, 1, 104),(34, 1, 105),(35, 1, 106),
-- ops admin
(40, 2, 1),(41, 2, 10),(42, 2, 20),(43, 2, 30),(44, 2, 40),(45, 2, 50),(46, 2, 60),
(50, 2, 11),(51, 2, 12),(52, 2, 21),(53, 2, 22),(54, 2, 23),(55, 2, 31),(56, 2, 32),(57, 2, 33),(58, 2, 34),
(60, 2, 100),(61, 2, 101),(62, 2, 102),(63, 2, 103),
-- finance
(70, 3, 1),(71, 3, 70),(72, 3, 120),(73, 3, 130),
-- warehouse
(80, 4, 1),(81, 4, 30),(82, 4, 31),(83, 4, 32),(84, 4, 33),(85, 4, 34),
-- customer service
(90, 5, 1),(91, 5, 10),(92, 5, 50),(93, 5, 11),(94, 5, 12),
(95, 5, 101);

-- ============================================================
-- erp_product 数据库
-- ============================================================
USE `erp_product`;

-- 商品分类
INSERT INTO `t_product_category` (`id`, `parent_id`, `category_name`, `category_name_en`, `category_code`, `sort_order`, `status`) VALUES
(1, 0, '电子产品', 'Electronics',     'ELECTRONICS', 1,  1),
(2, 1, '手机配件', 'Phone Accessories','PHONE_ACC',   1,  1),
(3, 1, '音频设备', 'Audio Devices',   'AUDIO',       2,  1),
(4, 1, '智能穿戴', 'Wearables',       'WEARABLE',    3,  1),
(5, 0, '家居用品', 'Home & Kitchen',  'HOME',        2,  1),
(6, 5, '厨房用品', 'Kitchen',         'KITCHEN',     1,  1),
(7, 5, '收纳整理', 'Storage',         'STORAGE',     2,  1),
(8, 0, '服装鞋帽', 'Apparel',         'APPAREL',     3,  1),
(9, 8, '男装',     'Mens',             'MENS',        1,  1),
(10, 8, '女装',    'Womens',          'WOMENS',      2,  1),
(11, 0, '美妆个护', 'Beauty',         'BEAUTY',      4,  1);

-- 商品主表
INSERT INTO `t_product` (`id`, `internal_sku`, `product_name`, `product_name_en`, `category_id`, `brand`, `main_image`, `description`, `weight`, `status`) VALUES
(1001, 'SKU-BLU-001',  '无线蓝牙耳机 Pro',  'Wireless Bluetooth Earbuds Pro',  3, 'AcmeSound', '/img/blu-001.jpg', '主动降噪 30h 续航',     0.20, 1),
(1002, 'SKU-WAT-002',  '智能手表 X1',      'Smart Watch X1',                  4, 'TechWear',  '/img/wat-002.jpg', '心率/血氧/GPS',          0.08, 1),
(1003, 'SKU-USB-003',  '65W GaN 充电器',   '65W GaN USB-C Charger',           2, 'ChargeMax', '/img/usb-003.jpg', '笔记本/手机通用',        0.15, 1),
(1004, 'SKU-STN-004',  '便携手机支架',     'Portable Phone Stand',            2, 'AcmeSound', '/img/stn-004.jpg', '折叠/铝制',              0.20, 1),
(1005, 'SKU-PWB-005',  '20000mAh 充电宝',  '20000mAh Power Bank',             2, 'ChargeMax', '/img/pwb-005.jpg', '双向快充',               0.45, 1),
(1006, 'SKU-KIT-006',  '硅胶厨具套装',     'Silicone Kitchen Set',            6, 'HomePlus',  '/img/kit-006.jpg', '6 件套耐高温',           0.80, 1),
(1007, 'SKU-STG-007',  '真空收纳袋 10 件','Vacuum Storage Bags 10pcs',      7, 'HomePlus',  '/img/stg-007.jpg', '节省 75% 空间',          0.50, 1),
(1008, 'SKU-MEN-008',  '男士速干 T 恤',   'Men Quick-Dry T-Shirt',           9, 'FitWear',   '/img/men-008.jpg', '透气弹力',               0.25, 1),
(1009, 'SKU-WOM-009',  '女士瑜伽裤',       'Women Yoga Pants',                10,'FitWear',   '/img/wom-009.jpg', '高腰弹力',               0.30, 1),
(1010, 'SKU-BTY-010',  '精华液 30ml',     'Anti-Aging Serum 30ml',           11,'GlowLab',   '/img/bty-010.jpg', '玻尿酸烟酰胺',           0.10, 1);

-- 商品 SKU
INSERT INTO `t_product_sku` (`id`, `product_id`, `sku_code`, `sku_name`, `specs`, `cost_price`, `sale_price`, `status`) VALUES
(1,  1001, 'SKU-BLU-001-BLK', '蓝牙耳机-黑色', '{"color":"黑色","model":"Pro"}',     120.00, 299.00, 1),
(2,  1001, 'SKU-BLU-001-WHT', '蓝牙耳机-白色', '{"color":"白色","model":"Pro"}',     120.00, 299.00, 1),
(3,  1002, 'SKU-WAT-002-BLK', '手表-黑色',     '{"color":"黑色","size":"42mm"}',     280.00, 599.00, 1),
(4,  1002, 'SKU-WAT-002-SLV', '手表-银色',     '{"color":"银色","size":"42mm"}',     280.00, 599.00, 1),
(5,  1003, 'SKU-USB-003-US',  '充电器-美规',   '{"plug":"US"}',                       35.00,  89.00,  1),
(6,  1003, 'SKU-USB-003-EU',  '充电器-欧规',   '{"plug":"EU"}',                       35.00,  89.00,  1),
(7,  1004, 'SKU-STN-004',     '手机支架-银',   '{"color":"银色","material":"铝"}',    12.00,  39.00,  1),
(8,  1005, 'SKU-PWB-005-BLK', '充电宝-黑',     '{"color":"黑色","capacity":"20000mAh"}',80.00,199.00,1),
(9,  1005, 'SKU-PWB-005-WHT', '充电宝-白',     '{"color":"白色","capacity":"20000mAh"}',80.00,199.00,1),
(10, 1006, 'SKU-KIT-006-RED', '厨具-红色',     '{"color":"红色","set":"6件套"}',       45.00, 129.00, 1),
(11, 1007, 'SKU-STG-007',     '收纳袋-混装',   '{"qty":"10件"}',                       18.00,  49.00,  1),
(12, 1008, 'SKU-MEN-008-L',   'T恤-L',         '{"size":"L","gender":"M"}',            30.00,  89.00,  1),
(13, 1008, 'SKU-MEN-008-XL',  'T恤-XL',        '{"size":"XL","gender":"M"}',           30.00,  89.00,  1),
(14, 1009, 'SKU-WOM-009-M',   '瑜伽裤-M',      '{"size":"M","gender":"F"}',            48.00, 129.00,  1),
(15, 1009, 'SKU-WOM-009-L',   '瑜伽裤-L',      '{"size":"L","gender":"F"}',            48.00, 129.00,  1),
(16, 1010, 'SKU-BTY-010',     '精华液 30ml',   '{"volume":"30ml"}',                    70.00, 199.00,  1);

-- 平台商品映射
INSERT INTO `t_platform_product_mapping` (`id`, `internal_sku`, `platform`, `shop_id`, `platform_sku`, `mapping_data`) VALUES
(1, 'SKU-BLU-001-BLK', 'amazon',  'US001', 'B07XJ12345', '{"asin":"B07XJ12345","marketplace":"US"}'),
(2, 'SKU-BLU-001-WHT', 'amazon',  'US001', 'B07XJ12346', '{"asin":"B07XJ12346","marketplace":"US"}'),
(3, 'SKU-WAT-002-BLK', 'amazon',  'US001', 'B08ABC0001', '{"asin":"B08ABC0001"}'),
(4, 'SKU-USB-003-US',  'amazon',  'US001', 'B0CD000123', '{"asin":"B0CD000123"}'),
(5, 'SKU-BLU-001-BLK', 'ebay',    'US001', 'EB-12345',   '{"listingId":"EB-12345"}'),
(6, 'SKU-WAT-002-BLK', 'shopee',  'MY001', 'SHP-998877', '{"itemId":998877}'),
(7, 'SKU-USB-003-US',  'shopee',  'MY001', 'SHP-998878', '{"itemId":998878}'),
(8, 'SKU-PWB-005-BLK', 'tiktok',  'US001', 'TTK-556677', '{"productId":"TTK-556677"}'),
(9, 'SKU-KIT-006-RED', 'amazon',  'US001', 'B0EF100001', '{"asin":"B0EF100001"}'),
(10,'SKU-WOM-009-M',   'shopee',  'MY001', 'SHP-998879', '{"itemId":998879}');

-- ============================================================
-- erp_warehouse 数据库
-- ============================================================
USE `erp_warehouse`;

-- 仓库
INSERT INTO `t_warehouse` (`id`, `warehouse_code`, `warehouse_name`, `warehouse_type`, `country`, `city`, `address`, `contact_person`, `contact_phone`, `status`) VALUES
(1, 'WH-CN-SH', '上海中心仓',    'OWN',   'CN', '上海', '上海市青浦区华新路 123 号', '王仓管', '13800138003', 1),
(2, 'WH-CN-GZ', '广州南沙保税仓','BONDED','CN', '广州', '广州市南沙区龙穴岛 88 号',  '李仓管', '13800138013', 1),
(3, 'WH-US-LA', '美国洛杉矶仓',  'OVERSEAS','US', 'Los Angeles', '1000 Logistics Way, LA, CA', 'Mike', '+1-213-555-0100', 1),
(4, 'WH-DE-HH', '德国汉堡仓',    'OVERSEAS','DE', 'Hamburg', 'Hafenstraße 8, 20457 Hamburg', 'Hans', '+49-40-555-0100', 1),
(5, 'WH-JP-TK', '日本东京仓',    'OVERSEAS','JP', 'Tokyo', '3-1-1 Shinagawa, Tokyo', 'Yamada', '+81-3-555-0100', 1);

-- 出入库单
INSERT INTO `t_warehouse_in_out` (`id`, `order_no`, `warehouse_id`, `in_out_type`, `status`, `total_items`, `operator`, `remark`) VALUES
(1, 'IN-2026-0001', 1, 'IN',  'completed', 1, '王仓管', '采购入库-蓝牙耳机'),
(2, 'IN-2026-0002', 1, 'IN',  'completed', 1, '王仓管', '采购入库-手表'),
(3, 'IN-2026-0003', 1, 'IN',  'completed', 1, '王仓管', '采购入库-充电器'),
(4, 'OUT-2026-0001',1, 'OUT', 'completed', 1, '王仓管', '出库到亚马逊仓'),
(5, 'OUT-2026-0002',1, 'OUT', 'completed', 1, '王仓管', '出库到 LA 仓'),
(6, 'IN-2026-0004', 2, 'IN',  'completed', 1, '李仓管', '保税仓入库-充电宝'),
(7, 'OUT-2026-0003',3, 'OUT', 'completed', 1, 'Mike',   '本地配送'),
(8, 'OUT-2026-0004',4, 'OUT', 'completed', 1, 'Hans',   '欧洲本地配送');

INSERT INTO `t_warehouse_in_out_item` (`id`, `in_out_id`, `sku_code`, `quantity`) VALUES
(1, 1, 'SKU-BLU-001-BLK', 500),
(2, 2, 'SKU-WAT-002-BLK', 200),
(3, 3, 'SKU-USB-003-US',  1000),
(4, 4, 'SKU-BLU-001-BLK', 80),
(5, 5, 'SKU-WAT-002-BLK', 30),
(6, 6, 'SKU-PWB-005-BLK', 300),
(7, 7, 'SKU-BLU-001-BLK', 50),
(8, 8, 'SKU-WAT-002-SLV', 20);

-- ============================================================
-- erp_inventory 数据库
-- ============================================================
USE `erp_inventory`;

INSERT INTO `t_inventory` (`id`, `sku_code`, `warehouse_id`, `available_qty`, `locked_qty`, `total_qty`, `safety_stock`, `min_stock`, `max_stock`) VALUES
(1, 'SKU-BLU-001-BLK', 1, 400, 20, 420, 100, 80,  1000),
(2, 'SKU-BLU-001-WHT', 1, 270, 10, 280, 80,  60,  800),
(3, 'SKU-WAT-002-BLK', 1, 160, 10, 170, 50,  40,  500),
(4, 'SKU-WAT-002-SLV', 1, 125, 5,  130, 50,  40,  500),
(5, 'SKU-USB-003-US',  1, 920, 30, 950, 200, 150, 2000),
(6, 'SKU-USB-003-EU',  1, 740, 20, 760, 150, 100, 1500),
(7, 'SKU-STN-004',     1, 1920,30, 1950,300,200, 3000),
(8, 'SKU-PWB-005-BLK', 1, 530, 20, 550, 100, 80,  1000),
(9, 'SKU-PWB-005-WHT', 1, 360, 20, 380, 80,  60,  800),
(10,'SKU-KIT-006-RED', 1, 460, 20, 480, 100, 80,  800),
(11,'SKU-STG-007',     1, 1420,30, 1450,200,150, 2000),
(12,'SKU-MEN-008-L',   1, 280, 10, 290, 50,  40,  500),
(13,'SKU-MEN-008-XL',  1, 280, 10, 290, 50,  40,  500),
(14,'SKU-WOM-009-M',   1, 380, 10, 390, 80,  60,  600),
(15,'SKU-WOM-009-L',   1, 380, 10, 390, 80,  60,  600),
(16,'SKU-BTY-010',     1, 760, 20, 780, 150, 100, 1200),
(17,'SKU-BLU-001-BLK', 3, 48,  2,  50,  20,  15,  200),
(18,'SKU-WAT-002-SLV', 4, 18,  2,  20,  10,  8,   100),
(19,'SKU-BLU-001-BLK', 5, 28,  2,  30,  10,  8,   100);

-- 库存日志
INSERT INTO `t_inventory_log` (`id`, `sku_code`, `warehouse_id`, `change_type`, `change_qty`, `before_qty`, `after_qty`, `order_no`, `remark`) VALUES
(1, 'SKU-BLU-001-BLK', 1, 'in',  500, 0,   500, 'IN-2026-0001',  '采购入库'),
(2, 'SKU-BLU-001-BLK', 1, 'out', 80,  500, 420, 'OUT-2026-0001', '出库到亚马逊仓'),
(3, 'SKU-WAT-002-BLK', 1, 'in',  200, 0,   200, 'IN-2026-0002',  '采购入库'),
(4, 'SKU-WAT-002-BLK', 1, 'out', 30,  200, 170, 'OUT-2026-0002', '出库到 LA 仓'),
(5, 'SKU-USB-003-US',  1, 'in',  1000,0,   1000,'IN-2026-0003',  '采购入库'),
(6, 'SKU-USB-003-US',  1, 'out', 50,  1000,950, NULL,           '销售出库'),
(7, 'SKU-PWB-005-BLK', 1, 'out', 50,  600, 550, NULL,           '销售出库'),
(8, 'SKU-MEN-008-L',   1, 'in',  -10, 300,290, NULL,           '盘点调整');

-- ============================================================
-- erp_order 数据库
-- ============================================================
USE `erp_order`;

-- 订单（30 单模拟 2026 上半年趋势）
INSERT INTO `t_order` (`id`, `platform`, `shop_id`, `platform_order_no`, `internal_order_no`, `buyer_name`, `buyer_email`, `buyer_phone`, `order_amount`, `product_amount`, `shipping_amount`, `tax_amount`, `currency_code`, `status`, `payment_status`, `payment_method`, `payment_time`, `shipping_method`, `logistics_company`, `tracking_number`, `recipient_name`, `recipient_country`, `recipient_city`, `recipient_address`, `recipient_postal_code`, `recipient_phone`, `remark`) VALUES
(1, 'amazon', 'US001', 'AMZ-111-0001', 'IO-202603-0001', 'John Smith',  'john@us.com',  '+1-213-555-0101', 299.00, 250.00, 30.00, 19.00, 'USD', 'SHIPPED',   'PAID', 'CREDIT_CARD', '2026-03-12 09:15:00', 'YANWEN',   'YANWEN',     'YT1234567890', 'John Smith', 'US', 'Los Angeles', '100 Main St', '90001', '+1-213-555-0101', NULL),
(2, 'shopee', 'MY001', 'SHP-998877001','IO-202603-0002', 'Ahmad bin Ali','ahmad@my.com','+60-12-345-6789', 599.00, 540.00, 50.00, 9.00, 'MYR', 'COMPLETED', 'PAID', 'GRABPAY',    '2026-03-15 14:22:00', 'NINJAVAN', 'NINJAVAN',   'NJ9988771',    'Ahmad',      'MY', 'Kuala Lumpur','12 Jalan Sultan','50000','+60-12-345-6789', NULL),
(3, 'amazon', 'US001', 'AMZ-111-0002', 'IO-202603-0003', 'Mary Johnson','mary@us.com', '+1-213-555-0102', 89.00,  80.00,  9.00,  0.00, 'USD', 'PENDING',   'PAID', 'PAYPAL',     '2026-03-20 11:00:00', NULL,       NULL,        NULL,           'Mary Johnson','US','San Francisco','500 Market St','94105', '+1-213-555-0102', NULL),
(4, 'ebay',   'US001', 'EB-12345-001', 'IO-202604-0001', 'Hans Müller', 'hans@de.com',  '+49-30-555-0101', 199.00, 180.00, 15.00, 4.00, 'EUR', 'SHIPPED',   'PAID', 'PAYPAL',     '2026-04-02 16:30:00', 'DHL',      'DHL',        'DH1234567890', 'Hans',       'DE', 'Berlin',      'Hauptstr. 1', '10115', '+49-30-555-0101', NULL),
(5, 'amazon', 'US001', 'AMZ-111-0003', 'IO-202604-0002', '李雷',       'lilei@cn.com', '+86-138-0000-0001', 89.00, 80.00, 9.00, 0.00, 'CNY', 'SHIPPED',   'PAID', 'ALIPAY',     '2026-04-10 10:30:00', 'SF',       'SF Express', 'SF1234567890','李雷',         'CN','深圳',         '南山路 100 号','518000','+86-138-0000-0001', NULL),
(6, 'shopee', 'MY001', 'SHP-998877002','IO-202604-0003', 'Tan Bee Ling','tan@my.com',  '+60-12-345-6790', 89.00, 80.00,  9.00, 0.00, 'MYR', 'COMPLETED', 'PAID', 'GRABPAY',    '2026-04-15 19:00:00', 'NINJAVAN', 'NINJAVAN',   'NJ9988772',    'Tan',        'MY', 'Penang',      '100 Beach St', '10200','+60-12-345-6790', NULL),
(7, 'amazon', 'US001', 'AMZ-111-0004', 'IO-202604-0004', 'Sarah Brown', 'sarah@us.com', '+1-312-555-0103', 199.00, 180.00,15.00, 4.00,'USD', 'PROCESSING','PAID', 'CREDIT_CARD','2026-04-18 14:00:00', NULL,       NULL,        NULL,           'Sarah',      'US', 'Chicago',     '233 Michigan Ave','60601','+1-312-555-0103', NULL),
(8, 'tiktok', 'US001', 'TTK-556677001','IO-202604-0005', 'Mike Davis',  'mike@us.com',  '+1-415-555-0104', 49.00,  40.00,  9.00, 0.00, 'USD', 'SHIPPED',   'PAID', 'TIKTOK_PAY', '2026-04-22 12:00:00', 'USPS',     'USPS',       'US1234567890', 'Mike',       'US', 'San Francisco','600 Mission St','94105','+1-415-555-0104', NULL),
(9, 'amazon', 'US001', 'AMZ-111-0005', 'IO-202605-0001', 'Emma Wilson', 'emma@uk.com',  '+44-20-555-0101', 599.00, 540.00,40.00, 19.00,'GBP', 'SHIPPED',   'PAID', 'CREDIT_CARD','2026-05-03 09:30:00', 'YANWEN',   'YANWEN',     'YT1234567891','Emma',        'UK','London',       '1 Oxford St','WC1A1AA','+44-20-555-0101', NULL),
(10,'ebay',   'US001', 'EB-12345-002', 'IO-202605-0002', 'David Lee',   'david@us.com', '+1-213-555-0105', 89.00,  80.00,  9.00, 0.00, 'USD', 'COMPLETED', 'PAID', 'PAYPAL',     '2026-05-08 18:30:00', 'USPS',     'USPS',       'US1234567891','David',       'US','Seattle',      '100 Pike St','98101', '+1-213-555-0105', NULL),
(11,'amazon', 'US001', 'AMZ-111-0006', 'IO-202605-0003', 'Frank Chen',  'frank@ca.com', '+1-416-555-0101', 299.00, 250.00,30.00, 19.00,'CAD', 'SHIPPED',   'PAID', 'CREDIT_CARD','2026-05-12 11:00:00', 'YANWEN',   'YANWEN',     'YT1234567892','Frank',       'CA','Toronto',      '100 King St W','M5X1C7','+1-416-555-0101', NULL),
(12,'shopee', 'MY001', 'SHP-998877003','IO-202605-0004', 'Lim Ah Beng', 'lim@my.com',   '+60-12-345-6791', 199.00, 180.00,15.00, 4.00,'MYR','PENDING',   'PAID', 'GRABPAY',    '2026-05-18 08:30:00', NULL,       NULL,        NULL,           'Lim',        'MY','Johor Bahru',  '50 Jalan Tun','80000','+60-12-345-6791', NULL),
(13,'tiktok', 'US001', 'TTK-556677002','IO-202605-0005', 'Anna Lee',    'anna@us.com',  '+1-213-555-0106', 199.00, 180.00,15.00, 4.00, 'USD','SHIPPED',   'PAID', 'TIKTOK_PAY', '2026-05-22 14:00:00', 'USPS',     'USPS',       'US1234567892','Anna',        'US','Phoenix',      '100 Washington St','85003','+1-213-555-0106', NULL),
(14,'amazon', 'US001', 'AMZ-111-0007', 'IO-202605-0006', 'Lisa Wang',   'lisa@cn.com',  '+86-138-0000-0002', 89.00, 80.00, 9.00, 0.00,'CNY','COMPLETED', 'PAID', 'ALIPAY',     '2026-05-25 16:00:00', 'SF',       'SF Express', 'SF1234567891','Lisa',        'CN','北京',          '中关村大街 1 号','100080','+86-138-0000-0002', NULL),
(15,'ebay',   'US001', 'EB-12345-003', 'IO-202606-0001', 'Tom Brown',   'tom@au.com',   '+61-2-555-0101', 599.00, 540.00,40.00, 19.00,'AUD', 'SHIPPED',   'PAID', 'PAYPAL',     '2026-06-03 10:00:00', 'YANWEN',   'YANWEN',     'YT1234567893','Tom',         'AU','Sydney',       '100 George St','2000','+61-2-555-0101', NULL),
(16,'amazon', 'US001', 'AMZ-111-0008', 'IO-202606-0002', 'Helen Kim',   'helen@kr.com', '+82-2-555-0101', 89.00, 80.00, 9.00, 0.00,'USD','PENDING',   'PAID', 'CREDIT_CARD','2026-06-08 13:30:00', NULL,       NULL,        NULL,           'Helen',      'KR','Seoul',        '1 Gangnam-da-ro','06236','+82-2-555-0101', NULL),
(17,'shopee', 'MY001', 'SHP-998877004','IO-202606-0003', '王大明',     'wang@tw.com',   '+886-9-1234-5678', 199.00, 180.00,15.00, 4.00,'TWD','SHIPPED',   'PAID', 'GRABPAY',    '2026-06-12 17:00:00', 'NINJAVAN', 'NINJAVAN',   'NJ9988773',   '王',          'TW','台北',         '信义路 100 号','110','+886-9-1234-5678', NULL),
(18,'amazon', 'US001', 'AMZ-111-0009', 'IO-202606-0004', 'Peter Zhang', 'peter@cn.com', '+86-138-0000-0003', 89.00, 80.00, 9.00, 0.00,'CNY','SHIPPED',   'PAID', 'ALIPAY',     '2026-06-15 11:30:00', 'SF',       'SF Express', 'SF1234567892','Peter',       'CN','上海',          '南京路 100 号','200002','+86-138-0000-0003', NULL),
(19,'tiktok', 'US001', 'TTK-556677003','IO-202606-0005', 'Sophie Wang', 'sophie@us.com','+1-213-555-0107', 49.00, 40.00, 9.00, 0.00,'USD','COMPLETED', 'PAID', 'TIKTOK_PAY', '2026-06-20 09:30:00', 'USPS',     'USPS',       'US1234567893','Sophie',     'US','New York',     '500 5th Ave','10110','+1-213-555-0107', NULL),
(20,'ebay',   'US001', 'EB-12345-004', 'IO-202606-0006', 'Carlos Lopez','carlos@mx.com','+52-55-555-0101', 299.00, 250.00,30.00, 19.00,'USD','SHIPPED',   'PAID', 'PAYPAL',     '2026-06-25 15:00:00', 'YANWEN',   'YANWEN',     'YT1234567894','Carlos',     'MX','Mexico City',  'Av. Reforma 100','06600','+52-55-555-0101', NULL),
(21,'amazon', 'US001', 'AMZ-111-0010', 'IO-202607-0001', '张伟',       'zhangwei@cn.com','+86-138-0000-0004', 199.00, 180.00,15.00, 4.00,'CNY','PENDING',   'PAID', 'WECHAT_PAY', '2026-07-01 12:00:00', NULL,       NULL,        NULL,           '张伟',        'CN','广州',          '天河路 100 号','510000','+86-138-0000-0004', NULL),
(22,'amazon', 'US001', 'AMZ-111-0011', 'IO-202607-0002', 'Daniel Smith','daniel@us.com','+1-617-555-0101', 599.00, 540.00,40.00, 19.00,'USD','PROCESSING','PAID', 'CREDIT_CARD','2026-07-03 16:30:00', NULL,       NULL,        NULL,           'Daniel',     'US','Boston',       '100 Tremont St','02108','+1-617-555-0101', NULL),
(23,'shopee', 'MY001', 'SHP-998877005','IO-202607-0003', 'Nurul Aisyah','nurul@id.com','+62-21-555-0101', 89.00, 80.00, 9.00, 0.00,'IDR','SHIPPED',   'PAID', 'GRABPAY',    '2026-07-05 08:00:00', 'NINJAVAN', 'NINJAVAN',   'NJ9988774',   'Nurul',       'ID','Jakarta',      'Jl. Sudirman 1','10110','+62-21-555-0101', NULL),
(24,'ebay',   'US001', 'EB-12345-005', 'IO-202607-0004', 'Anna Schmidt','anna@de.com',  '+49-89-555-0101', 199.00, 180.00,15.00, 4.00,'EUR','CANCELLED', 'UNPAID','CREDIT_CARD', NULL,                       NULL,       NULL,        NULL,           'Anna',       'DE','Munich',       'Marienplatz 1','80331','+49-89-555-0101', NULL),
(25,'tiktok', 'US001', 'TTK-556677004','IO-202607-0005', 'Kevin Chen',  'kevin@us.com', '+1-213-555-0108', 89.00, 80.00, 9.00, 0.00,'USD','SHIPPED',   'PAID', 'TIKTOK_PAY', '2026-07-07 14:00:00', 'USPS',     'USPS',       'US1234567894','Kevin',      'US','Houston',      '1000 Main St','77002','+1-213-555-0108', NULL),
(26,'amazon', 'US001', 'AMZ-111-0012', 'IO-202607-0006', 'Mary Johnson','mary2@us.com', '+1-213-555-0109', 49.00, 40.00, 9.00, 0.00,'USD','PENDING',   'PAID', 'PAYPAL',     '2026-07-08 10:00:00', NULL,       NULL,        NULL,           'Mary',       'US','Miami',        '100 Ocean Dr','33139','+1-213-555-0109', NULL),
(27,'amazon', 'US001', 'AMZ-111-0013', 'IO-202605-0007', 'James Wilson','james@uk.com', '+44-20-555-0102', 89.00, 80.00, 9.00, 0.00,'GBP','COMPLETED', 'PAID', 'CREDIT_CARD','2026-05-28 14:00:00', 'DHL',      'DHL',        'DH1234567892','James',      'UK','Manchester',   '100 Market St','M11AA','+44-20-555-0102', NULL),
(28,'shopee', 'MY001', 'SHP-998877006','IO-202606-0007', 'Lee Soo Min', 'lee@kr.com',   '+82-2-555-0102', 89.00, 80.00, 9.00, 0.00,'MYR','SHIPPED',   'PAID', 'GRABPAY',    '2026-06-22 11:30:00', 'NINJAVAN', 'NINJAVAN',   'NJ9988775',   'Lee',        'SG','Singapore',    '100 Orchard Rd','238801','+82-2-555-0102', NULL),
(29,'ebay',   'US001', 'EB-12345-006', 'IO-202604-0006', 'Sophie Brown','sophieb@fr.com','+33-1-555-0101', 599.00, 540.00,40.00, 19.00,'EUR','COMPLETED', 'PAID', 'PAYPAL',     '2026-04-26 09:30:00', 'YANWEN',   'YANWEN',     'YT1234567895','Sophie',     'FR','Paris',        '100 Champs','75001','+33-1-555-0101', NULL),
(30,'tiktok', 'US001', 'TTK-556677005','IO-202606-0008', '李娜',       'lina@cn.com',  '+86-138-0000-0005', 89.00, 80.00, 9.00, 0.00,'CNY','PENDING',   'PAID', 'TIKTOK_PAY', '2026-06-28 17:00:00', NULL,       NULL,        NULL,           '李娜',        'CN','成都',          '春熙路 100 号','610000','+86-138-0000-0005', NULL);

-- 订单商品
INSERT INTO `t_order_item` (`id`, `order_id`, `internal_sku`, `product_name`, `unit_price`, `quantity`, `total_amount`, `currency_code`) VALUES
(1, 1, 'SKU-BLU-001-BLK','蓝牙耳机-黑色', 299.00, 1, 299.00, 'USD'),
(2, 2, 'SKU-WAT-002-BLK','手表-黑色',     599.00, 1, 599.00, 'MYR'),
(3, 3, 'SKU-USB-003-US', '充电器-美规',   89.00,  1, 89.00,  'USD'),
(4, 4, 'SKU-PWB-005-BLK','充电宝-黑',     199.00, 1, 199.00, 'EUR'),
(5, 5, 'SKU-STN-004',    '手机支架-银',   39.00,  2, 78.00,  'CNY'),
(6, 5, 'SKU-USB-003-US', '充电器-美规',   89.00,  1, 89.00,  'CNY'),
(7, 6, 'SKU-USB-003-US', '充电器-美规',   89.00,  1, 89.00,  'MYR'),
(8, 7, 'SKU-PWB-005-BLK','充电宝-黑',     199.00, 1, 199.00, 'USD'),
(9, 8, 'SKU-STG-007',    '收纳袋-混装',   49.00,  1, 49.00,  'USD'),
(10,9, 'SKU-WAT-002-SLV','手表-银色',     599.00, 1, 599.00, 'GBP'),
(11,10,'SKU-KIT-006-RED','厨具-红色',     129.00, 1, 129.00, 'USD'),
(12,11,'SKU-BLU-001-BLK','蓝牙耳机-黑色', 299.00, 1, 299.00, 'CAD'),
(13,12,'SKU-PWB-005-BLK','充电宝-黑',     199.00, 1, 199.00, 'MYR'),
(14,13,'SKU-PWB-005-WHT','充电宝-白',     199.00, 1, 199.00, 'USD'),
(15,14,'SKU-USB-003-EU', '充电器-欧规',   89.00,  1, 89.00,  'CNY'),
(16,15,'SKU-WAT-002-BLK','手表-黑色',     599.00, 1, 599.00, 'AUD'),
(17,16,'SKU-STN-004',    '手机支架-银',   39.00,  2, 78.00,  'USD'),
(18,17,'SKU-BLU-001-WHT','蓝牙耳机-白色', 299.00, 1, 299.00, 'TWD'),
(19,18,'SKU-USB-003-EU', '充电器-欧规',   89.00,  1, 89.00,  'CNY'),
(20,19,'SKU-STG-007',    '收纳袋-混装',   49.00,  1, 49.00,  'USD'),
(21,20,'SKU-BLU-001-BLK','蓝牙耳机-黑色', 299.00, 1, 299.00, 'USD'),
(22,21,'SKU-PWB-005-BLK','充电宝-黑',     199.00, 1, 199.00, 'CNY'),
(23,22,'SKU-WAT-002-BLK','手表-黑色',     599.00, 1, 599.00, 'USD'),
(24,23,'SKU-USB-003-US', '充电器-美规',   89.00,  1, 89.00,  'IDR'),
(25,24,'SKU-PWB-005-WHT','充电宝-白',     199.00, 1, 199.00, 'EUR'),
(26,25,'SKU-STN-004',    '手机支架-银',   39.00,  2, 78.00,  'USD'),
(27,26,'SKU-STG-007',    '收纳袋-混装',   49.00,  1, 49.00,  'USD'),
(28,27,'SKU-USB-003-EU', '充电器-欧规',   89.00,  1, 89.00,  'GBP'),
(29,28,'SKU-USB-003-US', '充电器-美规',   89.00,  1, 89.00,  'MYR'),
(30,29,'SKU-WAT-002-BLK','手表-黑色',     599.00, 1, 599.00, 'EUR'),
(31,30,'SKU-USB-003-EU', '充电器-欧规',   89.00,  1, 89.00,  'CNY');

-- 同步日志（属于 erp_platform 平台配置库）
USE `erp_platform`;

-- 订单同步日志
INSERT INTO `t_sync_log` (`id`, `platform`, `shop_id`, `sync_type`, `start_time`, `end_time`, `success_count`, `fail_count`, `status`, `error_message`) VALUES
(1, 'amazon', 'US001', 'order',  '2026-03-12 09:00:00', '2026-03-12 09:05:00', 1,  0,  'success', NULL),
(2, 'shopee', 'MY001', 'order',  '2026-03-15 14:15:00', '2026-03-15 14:18:00', 1,  0,  'success', NULL),
(3, 'amazon', 'US001', 'order',  '2026-03-20 11:00:00', '2026-03-20 11:02:00', 1,  0,  'success', NULL),
(4, 'ebay',   'US001', 'order',  '2026-04-02 16:25:00', '2026-04-02 16:28:00', 1,  0,  'success', NULL),
(5, 'amazon', 'US001', 'product','2026-04-10 02:00:00', '2026-04-10 02:15:00', 25, 0,  'success', NULL),
(6, 'tiktok', 'US001', 'order',  '2026-04-22 11:55:00', '2026-04-22 11:58:00', 1,  0,  'success', NULL),
(7, 'shopee', 'MY001', 'order',  '2026-05-18 08:25:00', '2026-05-18 08:27:00', 1,  0,  'success', NULL),
(8, 'amazon', 'US001', 'order',  '2026-06-15 11:25:00', '2026-06-15 11:28:00', 1,  0,  'success', NULL),
(9, 'ebay',   'US001', 'order',  '2026-06-25 14:55:00', '2026-06-25 14:57:00', 1,  0,  'success', NULL),
(10,'shopee', 'MY001', 'order',  '2026-07-05 07:55:00', '2026-07-05 07:58:00', 1,  0,  'success', NULL),
(11,'amazon', 'US001', 'product','2026-07-06 02:00:00', '2026-07-06 02:15:00', 16, 1,  'failed',  'SKU-BLU-001-BLK 上架失败: 内容审核未通过');

-- ============================================================
-- erp_finance 数据库
-- ============================================================
USE `erp_finance`;

INSERT INTO `t_finance_flow` (`id`, `flow_type`, `order_no`, `amount`, `currency_code`, `category`, `remark`) VALUES
(1,  'income',  'IO-202603-0001', 299.00, 'USD', 'SALES',          '订单 IO-202603-0001'),
(2,  'income',  'IO-202603-0002', 599.00, 'MYR', 'SALES',          '订单 IO-202603-0002'),
(3,  'income',  'IO-202604-0001', 199.00, 'EUR', 'SALES',          '订单 IO-202604-0001'),
(4,  'income',  'IO-202604-0002', 89.00,  'CNY', 'SALES',          '订单 IO-202604-0002'),
(5,  'expense', NULL,             5000.00,'CNY', 'PURCHASE',       '蓝牙耳机采购'),
(6,  'income',  'IO-202605-0001', 599.00, 'GBP', 'SALES',          '订单 IO-202605-0001'),
(7,  'expense', NULL,             2000.00,'USD', 'LOGISTICS',      'DHL 国际运费'),
(8,  'income',  'IO-202605-0003', 299.00, 'CAD', 'SALES',          '订单 IO-202605-0003'),
(9,  'income',  'IO-202606-0001', 599.00, 'AUD', 'SALES',          '订单 IO-202606-0001'),
(10, 'income',  'IO-202606-0003', 199.00, 'TWD', 'SALES',          '订单 IO-202606-0003'),
(11, 'expense', NULL,             1500.00,'CNY', 'PLATFORM_FEE',   '亚马逊平台抽佣'),
(12, 'expense', NULL,             3200.00,'USD', 'ADVERTISING',    'Amazon Ads 投放'),
(13, 'income',  'IO-202607-0002', 599.00, 'USD', 'SALES',          '订单 IO-202607-0002'),
(14, 'income',  'IO-202607-0003', 89.00,  'IDR', 'SALES',          '订单 IO-202607-0003'),
(15, 'income',  'IO-202604-0006', 599.00, 'EUR', 'SALES',          '订单 IO-202604-0006');

-- ============================================================
-- 完
-- ============================================================
