create table Users
(
    id               int identity primary key,
    shop_name        nvarchar(255)                      not null
        constraint Users_pk unique
        constraint Users_pk2 unique,
    access_token     nvarchar(38)                       not null,
    email            nvarchar(320),
    phone            nvarchar(15),
    real_address     nvarchar(255),
    ip_address       nvarchar(255),
    user_tag         nvarchar(255),
    create_at        datetime2(0) default sysdatetime() not null,
    update_at        datetime2(0) default sysdatetime() not null,
    uninstall_time   datetime,
    first_name       nvarchar(255),
    last_name        nvarchar(255),
    login_time       datetime,
    encryption_email nvarchar(80)
)
go

