create table UserTrials
(
    id               int identity primary key,
    shop_name        nvarchar(255)      not null unique
        unique
        constraint UserTrials_pk
            unique,
    trial_start      datetime,
    trial_end        datetime,
    is_trial_expired bit,
    created_at       datetime default getdate(),
    updated_at       datetime default getdate(),
    is_trial_show    bit      default 0 not null
)
go

