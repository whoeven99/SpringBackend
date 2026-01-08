create table User_Liquid
(
    id                        int identity
        primary key,
    shop_name                 nvarchar(255)      not null,
    liquid_before_translation nvarchar(1000),
    liquid_after_translation  nvarchar(1000),
    language_code             nvarchar(10)       not null,
    is_deleted                bit      default 0 not null,
    created_at                datetime default getdate(),
    updated_at                datetime default getdate(),
    replacement_method        bit      default 0 not null
)
go