create table User_Private_Translate
(
    id          int identity
        primary key,
    api_name    int                not null,
    api_status  bit                not null,
    prompt_word nvarchar(max),
    api_model   nvarchar(255),
    token_limit bigint   default 0 not null,
    used_token  bigint   default 0 not null,
    is_selected bit      default 0 not null,
    created_at  datetime default getdate(),
    updated_at  datetime default getdate(),
    shop_name   nvarchar(255)      not null,
    api_key     nvarchar(255)      not null
)
go