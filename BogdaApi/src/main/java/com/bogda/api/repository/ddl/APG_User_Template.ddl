create table APG_User_Template
(
    id                   bigint identity
        primary key,
    template_data        nvarchar(800)      not null,
    template_title       nvarchar(200)      not null,
    template_type        nvarchar(100)      not null,
    template_description nvarchar(200)      not null,
    is_delete            bit      default 0 not null,
    create_time          datetime default getdate(),
    update_time          datetime default getdate(),
    template_model       nvarchar(30),
    template_subtype     nvarchar(30)
)
go