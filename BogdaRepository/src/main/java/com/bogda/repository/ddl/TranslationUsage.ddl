create table TranslationUsage
(
    translate_id      int           not null
        primary key,
    language_name     nvarchar(255) not null,
    credit_count      int           not null,
    consumed_time     int           not null,
    remaining_credits int           not null,
    created_at        datetime default getdate(),
    updated_at        datetime default getdate(),
    status            int           not null,
    shop_name         nvarchar(255) not null
)
go