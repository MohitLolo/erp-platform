package com.erp.report;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 报表服务启动类
 * <p>
 * 职责：查询 Apache Doris 数仓，提供销售分析、库存分析、财务报表、生产统计
 * 只读服务，不操作任何 OLTP 数据库；通过 MySQL 协议连接 Doris FE
 */
@SpringBootApplication(scanBasePackages = {"com.erp.report", "com.erp.common"})
@MapperScan("com.erp.report.infrastructure.mapper")
public class ReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportApplication.class, args);
    }
}
