create table APG_User_Template_Mapping
(
    id            bigint identity
        primary key,
    user_id       bigint not null,
    template_id   bigint not null,
    template_type bit    not null,
    is_delete     bit      default 0,
    create_time   datetime default getdate(),
    update_time   datetime default getdate()
)
go
