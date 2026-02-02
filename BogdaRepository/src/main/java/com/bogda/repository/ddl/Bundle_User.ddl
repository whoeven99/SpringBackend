create table Bundle_Users
(
    id           int identity primary key,
    shop_name    nvarchar(255)      not null,
    access_token nvarchar(38)       not null,
    email        nvarchar(320),
    user_tag     nvarchar(255),
    first_name   nvarchar(255),
    last_name    nvarchar(255),
    login_at    DATETIME DEFAULT GETUTCDATE(),
    uninstall_at DATETIME DEFAULT GETUTCDATE(),
    is_deleted   BIT      DEFAULT 0 NOT NULL,
    updated_at   DATETIME DEFAULT GETUTCDATE(),
    created_at   DATETIME DEFAULT GETUTCDATE()
)
go

ALTER TABLE Bundle_Users
    ADD CONSTRAINT UQ_Bundle_Users_shop_name UNIQUE (shop_name);
go