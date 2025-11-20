# 数据定义语言（DDL）规则

## 表字段要求
每个表必须包含以下字段：
- **id**: 主键，自增整数，`IDENTITY(1,1) PRIMARY KEY`
- **is_deleted**: 逻辑删除标志，布尔值，默认值为 `0`（未删除）
- **updated_at**: 记录最后更新时间，默认为当前时间
- **created_at**: 记录创建时间，默认为当前时间

## 字段格式要求
- **Integer**: `NOT NULL DEFAULT 0`
- **Varchar(255)**: `NOT NULL DEFAULT ''`

## 索引要求
每种 `SELECT` 查询必须添加索引。例如：
```sql
CREATE NONCLUSTERED INDEX IX_InitialTaskId_SavedToShopify_IsDeleted
ON dbo.Translate_Tasks_V2 (initial_task_id, saved_to_shopify, is_deleted);
```
