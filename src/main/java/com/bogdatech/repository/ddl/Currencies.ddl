create table Currencies
(
    id             int identity
        primary key,
    shop_name      nvarchar(50)                       not null,
    currency_code  char(3)                            not null,
    rounding       nvarchar(30),
    exchange_rate  nvarchar(50),
    create_at      datetime2(0) default sysdatetime() not null,
    update_at      datetime2(0) default sysdatetime() not null,
    currency_name  nvarchar(40)                       not null,
    primary_status int          default 0
)
go