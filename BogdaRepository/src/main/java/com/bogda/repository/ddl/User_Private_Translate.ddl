create table User_Private_Translate
(
    id          int identity
        primary key,
    api_name    int                not null,
    api_status  bit                not null,
    token_limit bigint   default 0 not null,
    used_token  bigint   default 0 not null,
    shop_name   nvarchar(255)      not null,
    api_key     nvarchar(255)      not null,
    created_at  datetime default getdate(),
    updated_at  datetime default getdate(),
    is_deleted  bit      default 0 not null
)

CREATE UNIQUE INDEX UX_User_Private_Translate_ApiName_ShopName
    ON User_Private_Translate (api_name, shop_name);

go