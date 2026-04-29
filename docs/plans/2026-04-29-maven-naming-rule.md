# Maven 命名规范（ERP Platform）

日期：2026-04-29

## 规则

1. `artifactId`
- 全部使用小写 kebab-case。
- 结构前缀固定：`erp-platform` / `erp-commons` / `erp-apis` / `erp-services` / `erp-common-*` / `erp-api-*` / `erp-*`。

2. `name`
- 统一使用与 `artifactId` 完全一致的技术名（不使用自然语言，不混用大小写）。
- 示例：`<name>erp-sale</name>`。

3. `description`
- 使用中文一句话职责说明。
- 风格要求：简洁、可检索、不过度营销；父模块描述其聚合职责，子模块描述其业务/技术职责。

4. 适用范围
- 本仓库所有 `pom.xml`（根、父模块、子模块）。

## 备注

- 本次仅统一命名，不调整 `groupId`/`artifactId` 及发布坐标。
