create table Glossary
(
    id             int identity
        primary key,
    shop_name      nvarchar(256)      not null,
    source_text    nvarchar(256),
    target_text    nvarchar(256),
    range_code     nvarchar(10),
    case_sensitive tinyint  default 0 not null,
    status         tinyint  default 0 not null,
    created_date   datetime default getutcdate(),
    updated_date   datetime default getutcdate()
)
go