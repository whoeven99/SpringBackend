-- auto-generated definition
create table WidgetConfigurations
(
    id                      int identity
        primary key,
    shop_name               nvarchar(255),
    language_selector       bit,
    currency_selector       bit,
    ip_open                 bit,
    included_flag           bit,
    font_color              nvarchar(50),
    background_color        nvarchar(50),
    button_color            nvarchar(50),
    button_background_color nvarchar(50),
    option_border_color     nvarchar(50),
    selector_position       nvarchar(20),
    position_data           nvarchar(20),
    created_at              datetime default getdate(),
    updated_at              datetime default getdate(),
    is_transparent          bit      default 0 not null
)
go