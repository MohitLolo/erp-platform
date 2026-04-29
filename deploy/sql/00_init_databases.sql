-- ============================================================
-- 00_init_databases.sql
-- 创建所有数据库（先于业务表执行）
-- ============================================================
CREATE DATABASE IF NOT EXISTS erp_system   DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS erp_base     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS erp_sale     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS erp_purchase DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS erp_inventory DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS erp_finance  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS erp_production DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS seata        DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 示例租户 Schema（多租户方案：每租户独立 Schema，ORM 动态切换）
CREATE DATABASE IF NOT EXISTS erp_tenant_001 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
