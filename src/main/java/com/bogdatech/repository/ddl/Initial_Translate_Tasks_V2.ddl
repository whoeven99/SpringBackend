CREATE TABLE dbo.Initial_Translate_Tasks_V2
(
    id                     INT IDENTITY (1,1) PRIMARY KEY,
    shop_name              NVARCHAR(64)   NOT NULL,
    source                 NVARCHAR(8)    NOT NULL,
    target                 NVARCHAR(8)    NOT NULL,
    status                 INT            NOT NULL,
    module_list            NVARCHAR(1000) NOT NULL,
    is_cover               BIT                     DEFAULT 0 NOT NULL,
    trans_model_type       NVARCHAR(25)   NOT NULL DEFAULT '',
    send_email             BIT                     DEFAULT 0 NOT NULL,
    init_minutes           INT            NOT NULL default 0,
    translation_minutes    INT            NOT NULL default 0,
    saving_shopify_minutes INT            NOT NULL default 0,
    used_token             INT            NOT NULL default 0,

    is_deleted             BIT                     DEFAULT 0 NOT NULL,
    updated_at             DATETIME                DEFAULT GETUTCDATE(),
    created_at             DATETIME                DEFAULT GETUTCDATE()
)
GO


CREATE NONCLUSTERED INDEX IX_ShopName_Source_TaskType_IsDeleted
    ON dbo.Initial_Translate_Tasks_V2(shop_name,source,task_type,is_deleted);

CREATE NONCLUSTERED INDEX IX_ShopName_IsDeleted
    ON dbo.Initial_Translate_Tasks_V2(shop_name,is_deleted);

CREATE NONCLUSTERED INDEX IX_TaskType_IsDeleted_CreatedAt
    ON dbo.Initial_Translate_Tasks_V2(task_type,is_deleted,created_at);

CREATE NONCLUSTERED INDEX IX_TaskType_Status_SendEmail_IsDeleted
    ON dbo.Initial_Translate_Tasks_V2(task_type,status,send_email,is_deleted);

ALTER TABLE dbo.Initial_Translate_Tasks_V2
    ADD task_type NVARCHAR(10) NOT NULL DEFAULT 'manual';