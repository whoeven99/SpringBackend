create table SubscriptionQuotaRecord
(
    id              int identity
        primary key,
    subscription_id nvarchar(255)      not null,
    billing_cycle   int                not null,
    created_at      datetime default getdate(),
    updated_at      datetime default getdate(),
    is_deleted      bit      default 0 not null
)
go