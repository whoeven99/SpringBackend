create table APG_Template
(
    id                   bigint identity
        primary key,
    template_data        nvarchar(800),
    template_title       nvarchar(200),
    template_type        int,
    template_seo         int,
    user_id              bigint,
    create_at            datetime default getutcdate(),
    update_at            datetime default getutcdate(),
    template_description nvarchar(200),
    is_delete            bit
)
go