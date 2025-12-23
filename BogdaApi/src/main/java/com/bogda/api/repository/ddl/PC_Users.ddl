create table PC_Users
(
    id              int identity
        primary key,
    shop_name       nvarchar(255)      not null
        unique,
    access_token    nvarchar(38)       not null,
    purchase_points int      default 0 not null,
    used_points     int      default 0 not null,
    email           nvarchar(320),
    phone           nvarchar(15),
    real_address    nvarchar(255),
    ip_address      nvarchar(255),
    user_tag        nvarchar(255),
    first_name      nvarchar(255),
    last_name       nvarchar(255),
    uninstall_time  datetime,
    login_time      datetime,
    is_deleted      int      default 0 not null,
    create_at       datetime default getdate(),
    update_at       datetime default getdate()
)
go
