CREATE TABLE dbo.Translate_Tasks_V2
(
    id                  INT             IDENTITY(1,1) PRIMARY KEY,
    initial_task_id     INT             NOT NULL DEFAULT 0,
    module              NVARCHAR(64)    NOT NULL default '',
    resource_id              NVARCHAR(128)    NOT NULL default '',
    node_key              NVARCHAR(64)    NOT NULL default '',
    type              NVARCHAR(64)    NOT NULL default '',
    digest              NVARCHAR(64)    NOT NULL default '',
    source_value        NVARCHAR(MAX)  NOT NULL default '',
    target_value        NVARCHAR(3000)  NOT NULL default '',
    has_target_value    Bit             Not null default 0,
    saved_to_shopify    Bit             NOT NULL default 0,
    is_deleted          BIT             NOT NULL DEFAULT 0,
    updated_at          DATETIME        DEFAULT GETUTCDATE(),
    created_at          DATETIME        DEFAULT GETUTCDATE()
)
GO

CREATE NONCLUSTERED INDEX IX_InitialTaskId_SavedToShopify_IsDeleted
ON dbo.Translate_Tasks_V2 (initial_task_id, saved_to_shopify, is_deleted);

CREATE NONCLUSTERED INDEX IX_InitialTaskId_TargetValue_IsDeleted
ON dbo.Translate_Tasks_V2 (initial_task_id, has_target_value, is_deleted);

CREATE NONCLUSTERED INDEX IX_InitialTaskId_Type_TargetValue_IsDeleted
ON dbo.Translate_Tasks_V2 (initial_task_id, type, has_target_value, is_deleted)