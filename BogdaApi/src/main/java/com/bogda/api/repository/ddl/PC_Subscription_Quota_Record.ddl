create table PC_Subscription_Quota_Record
(
    id              int identity
        primary key,
    subscription_id nvarchar(255)      not null,
    billing_cycle   int      default 0 not null,
    is_deleted      bit      default 0,
    created_at      datetime default getdate(),
    updated_at      datetime default getdate(),
    constraint UQ_PC_Subscription_Quota_Record_SubscriptionId_BillingCycle
        unique (subscription_id, billing_cycle)
)
go