create table APG_User_Counter
(
    id                     bigint identity
        primary key,
    user_id                bigint             not null
        constraint APG_User_Counter_pk
            unique,
    chars                  int      default 0 not null,
    product_counter        int      default 0 not null,
    product_seo_counter    int      default 0 not null,
    collection_counter     int      default 0 not null,
    collection_seo_counter int      default 0 not null,
    all_counter            int      default 5 not null,
    extra_counter          int      default 0 not null,
    user_token             int      default 0 not null,
    create_at              datetime default getutcdate(),
    update_at              datetime default getutcdate()
)
go