package top.mcxiafeng.badger.data

// ============================================================
// V1 DAO 已全部退役（Phase 3 + Phase 4）
// ============================================================
//
// - ContactFieldDao / CustomFieldDao / ContactFieldValueDao: Phase 3 Task #17 表已删
// - ScanResultDao: Phase 4 Task #20 表已删（MIGRATION_12_13）
// - ContactPlatformDao: Phase 4 Task #19 表已删（MIGRATION_11_12）
//
// V1 entity 类（ContactField / CustomField / ContactFieldValue）保留为 DTO，
// 由 FieldRepository / ContactMapper 消费，不再映射 Room 表。