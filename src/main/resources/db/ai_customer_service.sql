-- ??????????????????
-- ?? hmdp ??????????????????
CREATE TABLE IF NOT EXISTS `tb_home_service_appointment` (
  `id` bigint(20) NOT NULL COMMENT '????',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '??ID',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '??ID',
  `service_type` varchar(64) NOT NULL COMMENT '??????',
  `appointment_time` datetime NOT NULL COMMENT '??????',
  `contact_name` varchar(32) NOT NULL COMMENT '???',
  `contact_phone` varchar(16) NOT NULL COMMENT '????',
  `service_address` varchar(255) NOT NULL COMMENT '????',
  `remark` varchar(255) DEFAULT NULL COMMENT '??',
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0 pending, 1 confirmed, 2 user cancelled, 3 completed, 4 rejected, 5 timed out',
  `source` varchar(16) NOT NULL COMMENT 'AI_CHAT ? MANUAL',
  `idempotency_key` varchar(64) DEFAULT NULL COMMENT '???',
  `create_time` datetime NOT NULL COMMENT '????',
  `update_time` datetime NOT NULL COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_idempotency` (`user_id`, `idempotency_key`),
  KEY `idx_user_create_time` (`user_id`, `create_time`),
  KEY `idx_shop_appointment_time` (`shop_id`, `appointment_time`),
  KEY `idx_status_appointment_time` (`status`, `appointment_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='??????';
