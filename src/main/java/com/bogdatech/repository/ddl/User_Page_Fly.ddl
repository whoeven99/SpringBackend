create table User_Page_Fly
(
    id            int identity
        primary key,
    shop_name     nvarchar(255)      not null,
    source_text   nvarchar(1000),
    target_text   nvarchar(1000),
    language_code nvarchar(10)       not null,
    is_deleted    bit      default 0 not null,
    created_at    datetime default getdate(),
    updated_at    datetime default getdate(),
    constraint UQ_User_Page_Fly_Shop_Source_Target_Lang_IsDeleted
        unique (shop_name, source_text, target_text, language_code, is_deleted)
)
go