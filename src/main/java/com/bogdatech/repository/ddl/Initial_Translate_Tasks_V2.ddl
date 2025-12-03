CREATE TABLE dbo.Initial_Translate_Tasks_V2
(
    id                  INT             IDENTITY(1,1) PRIMARY KEY,
    shop_name           NVARCHAR(64)    NOT NULL,
    source              NVARCHAR(8)     NOT NULL,
    target              NVARCHAR(8)     NOT NULL,
    status              INT             NOT NULL,
    module_list         NVARCHAR(1000)  NOT NULL,
    is_cover            BIT             DEFAULT 0 NOT NULL,
    trans_model_type    NVARCHAR(8)     NOT NULL DEFAULT '',
    send_email          BIT             DEFAULT 0 NOT NULL,
    init_minutes              INT             NOT NULL default 0,
    translation_minutes              INT             NOT NULL default 0,
    saving_shopify_minutes              INT             NOT NULL default 0,
    used_token              INT             NOT NULL default 0,

    is_deleted          BIT             DEFAULT 0 NOT NULL,
    updated_at          DATETIME        DEFAULT GETUTCDATE(),
    created_at          DATETIME        DEFAULT GETUTCDATE()
)
GO

CREATE NONCLUSTERED INDEX IX_ShopName_Source_IsDeleted
ON dbo.Initial_Translate_Tasks_V2 (shop_name, source, is_deleted);

CREATE NONCLUSTERED INDEX IX_Status_IsDeleted
ON dbo.Initial_Translate_Tasks_V2 (status, is_deleted);

ALTER TABLE dbo.Initial_Translate_Tasks_V2
    ADD task_type NVARCHAR(10) NOT NULL DEFAULT 'manual';