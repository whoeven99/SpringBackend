create table TranslationCounter
(
    id            int identity
        primary key,
    shop_name     nvarchar(255)                      not null
        constraint TranslationCounter_pk
            unique
        constraint TranslationCounter_pk2
            unique,
    chars         int          default 0             not null,
    google_chars  int          default 0             not null,
    open_ai_chars int          default 0             not null,
    total_chars   int          default 0             not null,
    used_chars    int          default 0             not null,
    create_at     datetime2(0) default sysdatetime() not null,
    update_at     datetime2(0) default sysdatetime() not null
)
go