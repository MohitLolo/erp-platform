# smart-doc 使用说明（最小改造版）

## 目标

- 各服务文档输出到构建目录 `target/doc`，避免污染源码目录。
- 通过 Maven Profile 一键生成 `erp-services` 全量服务文档。

## 命令

在仓库根目录执行：

```bash
mvn -pl erp-services -am -Pdoc -DskipTests verify
```

## 输出位置

每个服务输出：

- `erp-services/<service>/target/doc/index.html`

例如：

- `erp-services/erp-system/target/doc/index.html`
- `erp-services/erp-sale/target/doc/index.html`

## 聚合到平台建议

- CI 收集 `erp-services/*/target/doc/index.html` 及相关资源。
- 按 `serviceName + version` 归档上传到文档平台。
- 平台侧统一做服务目录索引页（可后续补自动脚本）。
