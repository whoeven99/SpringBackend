create table PC_User_Trials
(
    id               int identity
        primary key,
    shop_name        nvarchar(255)      not null
        constraint UQ_PC_User_Trials_ShopName
            unique,
    trial_start      datetime,
    trial_end        datetime,
    is_trial_expired bit      default 0 not null,
    is_trial_show    bit      default 0 not null,
    is_deduct        bit      default 0 not null,
    is_deleted       bit      default 0,
    created_at       datetime default getdate(),
    updated_at       datetime default getdate()
)
go