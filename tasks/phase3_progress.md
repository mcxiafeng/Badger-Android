# Phase 3 Progress: V1 表退役 — 自定义字段值

## 完成的工作

### Task #15: 双写 ✓
- 修改 `FieldRepositoryImpl` 注入 V2 cache DAO (`ContactFieldCacheDao`, `ContactFieldValueCacheDao`)
- 所有写操作同时写 V1 表 + V2 cache 表
- 新增 V1→V2 entity 映射扩展函数 (`toCacheEntity()`)

### Task #16: 切读 + 回滚门控 ✓
- 新增 `FieldMigrationConfig` feature flag 对象
- 所有读操作根据 `useV2Reads` flag 选择 V1 或 V2 路径
- 新增 V2→V1 entity 映射扩展函数 (`toV1Entity()`)

### Task #28: 观察期（已启用）
- `FieldMigrationConfig.useV2Reads = true` 已启用
- 需要至少 1 个 commit 周期观察无回归

### Task #17: 删 V1 表（已准备，待观察期后执行）
- 已创建 `MIGRATION_9_10` 迁移代码（删除 `contact_field_values` / `custom_fields` / `contact_fields`）
- 待观察期通过后，需要：
  1. 更新 AppDatabase version = 10
  2. 从 @Database entities 移除 V1 entity
  3. 从 AppDatabase 移除 V1 DAO 抽象方法
  4. 从 KoinModules 移除 V1 DAO 绑定
  5. 清理 FieldRepositoryImpl 的 V1 依赖

## Code Review Findings（已修复）

1. **Critical**: `deleteFieldValue` 缺少 V2 cache 双写 → 已修复
2. **Required**: `updateFieldValueByKey` 未使用 feature flag → 已修复
3. **Required**: `deleteField` V1/V2 行为不一致 → 已统一使用软删除

## 待办事项

- [ ] Task #28 观察期通过后，执行 Task #17（删 V1 表）
- [ ] 考虑创建 `CustomFieldCacheEntity` 和 `CustomFieldCacheDao`（`custom_fields` 目前无 V2 cache 表）

## 验证结果

- `compileDebugKotlin` 绿
- 全量单测绿（362 tests，2 pre-existing flaky tests 单类重跑即绿）
