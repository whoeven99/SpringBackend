create table APG_Official_Template
(
    id                   bigint identity
        primary key,
    template_data        nvarchar(800),
    template_title       nvarchar(200),
    template_type        nvarchar(100),
    template_description nvarchar(200),
    used_times           int      default 0,
    create_time          datetime default getdate(),
    update_time          datetime default getdate(),
    template_model       nvarchar(30),
    template_subtype     nvarchar(30),
    is_payment           bit      default 0,
    example_date         nvarchar(800)
)
go