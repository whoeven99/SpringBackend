create table TranslateTextTable
(
    id           int identity
        primary key,
    shop_name    nvarchar(255),
    resource_id  nvarchar(255),
    text_type    nvarchar(255),
    digest       nvarchar(66),
    text_key     nvarchar(255),
    source_text  nvarchar(max),
    target_text  nvarchar(max),
    source_code  nvarchar(8),
    target_code  nvarchar(8),
    created_date datetime default getutcdate(),
    updated_date datetime default getutcdate()
)
go