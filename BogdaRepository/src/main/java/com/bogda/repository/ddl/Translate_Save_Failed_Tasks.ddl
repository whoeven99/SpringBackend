CREATE TABLE dbo.Translate_Save_Failed_Tasks
(
    id                INT IDENTITY (1,1) PRIMARY KEY,
    translate_task_id INT            NOT NULL DEFAULT 0,
    initial_task_id   INT            NOT NULL DEFAULT 0,
    shop_name         NVARCHAR(255)  NOT NULL DEFAULT '',
    error_message     NVARCHAR(1000) NOT NULL DEFAULT '',
    retry_count       INT            NOT NULL DEFAULT 0,
    retried           BIT            NOT NULL DEFAULT 0,
    is_deleted        BIT            NOT NULL DEFAULT 0,
    updated_at        DATETIME                DEFAULT GETUTCDATE(),
    created_at        DATETIME                DEFAULT GETUTCDATE()
)
GO

CREATE NONCLUSTERED INDEX IX_SaveFailed_InitialTaskId_Retried_IsDeleted
    ON dbo.Translate_Save_Failed_Tasks (initial_task_id, retried, is_deleted);

CREATE NONCLUSTERED INDEX IX_SaveFailed_Retried_IsDeleted_CreatedAt
    ON dbo.Translate_Save_Failed_Tasks (retried, is_deleted, created_at);

CREATE NONCLUSTERED INDEX IX_SaveFailed_ShopName_Retried_IsDeleted
    ON dbo.Translate_Save_Failed_Tasks (shop_name, retried, is_deleted);
