create table APG_User_Product
(
    id               bigint identity
        primary key,
    product_id       nvarchar(100)      not null,
    user_id          bigint             not null,
    create_vision    int      default 0,
    is_delete        bit      default 0 not null,
    update_time      datetime default getutcdate(),
    create_time      datetime default getutcdate(),
    generate_content nvarchar(max),
    page_type        nvarchar(20),
    content_type     nvarchar(20)
)
go