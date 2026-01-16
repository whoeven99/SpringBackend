CREATE TABLE dbo.Delete_Tasks
(
    id               INT IDENTITY (1,1) PRIMARY KEY,
    initial_task_id  INT            NOT NULL DEFAULT 0,
    resource_id      NVARCHAR(128)  NOT NULL default '',
    node_key         NVARCHAR(64)   NOT NULL default '',
    deleted_to_shopify Bit            NOT NULL default 0,
    is_deleted       BIT            NOT NULL DEFAULT 0,
    updated_at       DATETIME                DEFAULT GETUTCDATE(),
    created_at       DATETIME                DEFAULT GETUTCDATE()
)
go

CREATE INDEX IX_Delete_Tasks_Main
    ON dbo.Delete_Tasks(initial_task_id, resource_id, deleted_to_shopify, is_deleted)
go