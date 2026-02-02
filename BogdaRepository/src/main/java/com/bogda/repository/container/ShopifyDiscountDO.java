package com.bogda.repository.container;

import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Container(containerName = "products", autoCreateContainer = false)
public class ShopifyDiscountDO {
    @Id
    private String id;
    @PartitionKey
    private String shopName;
    private String discountGid;
    private String status;
    private DiscountData discountData;
    private String createdAt;
    private String updatedAt;

    @Data
    public static class DiscountData {
        @JsonProperty("product_pool")
        private ProductPool productPool;
        @JsonProperty("basic_information")
        private BasicInformation basicInformation;
        @JsonProperty("discount_rules")
        private List<DiscountRule> discountRules;
        @JsonProperty("style_config")
        private StyleConfig styleConfig;
        @JsonProperty("targeting_settings")
        private TargetingSettings targetingSettings;
        private Metafields metafields;
        private Subscription subscription;
        @JsonProperty("progressive_gift")
        private ProgressiveGift progressiveGift;

        @Data
        public static class BasicInformation {
            private String offerName;
            private String displayName;
            private OfferType offerType;


            @Data
            public static class OfferType {
                private String category;
                private String subtype;
            }
        }

        @Data
        private static class Subscription {
            private Boolean enable;
            private SubscriptionSettings settings;
            private SubscriptionStyle style;

            @Data
            private static class SubscriptionSettings {
                private String layout;
                private String position;
                @JsonProperty("subscription_title")
                private String subscriptionTitle;
                @JsonProperty("subscription_subtitle")
                private String subscriptionSubtitle;
                @JsonProperty("oneTime_title")
                private String oneTimeTitle;
                @JsonProperty("oneTime_subtitle")
                private String oneTimeSubtitle;
                private Boolean defaultSelected;
            }

            @Data
            private static class SubscriptionStyle {
                private Colors colors;
                private Sizes sizes;

                @Data
                private static class Colors {
                    private String title;
                    private String subtitle;
                }

                @Data
                private static class Sizes {
                    private String title;
                    private String subtitle;
                }
            }
        }

        @Data
        private static class ProgressiveGift {
            private Boolean enable;
            private Settings settings;
            private Style style;

            @Data
            private static class Settings {
                private String layout;
                private String position;
                private String title;
                private String subtitle;
                private Boolean hideTilUnlocked;
                private Boolean showLabels;
                private List<Gift> gifts;

                @Data
                private static class Gift {
                    private String type;
                    private Integer unlockedAt;
                    private String label;
                    private String labelCrossOut;
                    private String title;
                    private String lockedTitle;
                    private String imgUrl;
                    private Product product;

                    @Data
                    private static class Product {
                        private String id;
                        private List<String> variantId;
                        private String title;
                        private String imgUrl;
                        private Integer quantity;
                    }
                }
            }

            @Data
            private static class Style {
                private Colors colors;
                private Sizes sizes;
                @Data
                private static class Colors {
                    private String title;
                    private String subtitle;
                }

                @Data
                private static class Sizes {
                    private String title;
                    private String subtitle;

                }
            }
        }

        @Data
        public static class ProductPool {
            @JsonProperty("include_product_ids")
            private List<String> includeProductIds;
            @JsonProperty("include_variant_ids")
            private List<String> includeVariantIds;
            @JsonProperty("include_collection_ids")
            private List<String> includeCollectionIds;
        }

        @Data
        public static class DiscountRule {
            private String id;
            private Boolean enabled;
            private Boolean isExpanded;
            private String title;

            private Integer quantity;
            private Discount discount;
            @JsonProperty("discount_reward")
            private List<DiscountReward> discountRewards;
            private String subtitle;
            private String labelText;
            private String badgeText;
            private Boolean selectedByDefault;
            private List<Reward> reward;

            @Data
            public static class discountRewards {
                private String id;
                private Integer quantity;
                private Discount discount;

                @Data
                public static class Discount {
                    private String type;
                    private Double value;
                    private Double maxDiscount;
                }
            }

            @Data
            public static class Reward {
                private String type;
                private List<Products> products;
                private Discount discount;
                private Display display;

                @Data
                public static class Display {
                    private String text;
                    private Boolean showOriginalPrice;
                    private Boolean visibleWithoutCheck;
                }

                @Data
                public static class Products {
                    private String variantId;
                    private Integer quantity;
                }
            }

            @Data
            public static class Discount {
                private String type;
                private Double value;
                private Double maxDiscount;
            }

            @Data
            public static class DiscountReward {
                @JsonProperty("reward_item")
                private String rewardItem;
                @JsonProperty("reward_discount")
                private RewardDiscount rewardDiscount;

                @Data
                public static class RewardDiscount {
                    private String type;
                    private Integer value;
                    private String maxDiscount;
                }
            }
        }

        @Data
        public static class StyleConfig {
            private Layout layout;
            private Card card;
            private Title title;
            private Button button;
            private Countdown countdown;

            @Data
            public static class Layout {
                @JsonProperty("base_style")
                private String baseStyle;
            }

            @Data
            public static class Card {
                @JsonProperty("background_color")
                private String backgroundColor;
                @JsonProperty("border_color")
                private String borderColor;
                @JsonProperty("label_color")
                private String labelColor;
            }

            @Data
            public static class Title {
                private String text;
                private String fontSize;
                private String fontWeight;
                private String color;
            }

            @Data
            public static class Button {
                private String text;
                private String primaryColor;
            }

            @Data
            public static class Countdown {
                private Boolean enabled;
                private String duration;
                private String color;
            }
        }

        @Data
        public static class TargetingSettings {
            private List<String> marketVisibilitySettingData;
            private Eligibility eligibility;
            @JsonProperty("visibility_constraints")
            private VisibilityConstraints visibilityConstraints;
            @JsonProperty("usage_limit")
            private UsageLimit usageLimit;
            private Schedule schedule;
            private Budget budget;
            private Boolean showOfferToBots;
            @JsonProperty("quantity_scope")
            private String quantityScope;

            @Data
            public static class Eligibility {
                private String type;
                private List<String> customers;
                private List<String> segments;
                private Blacklist blacklist;

                @Data
                public static class Blacklist {
                    private List<String> emails;
                    private List<String> ips;
                }
            }

            @Data
            public static class VisibilityConstraints {
                private Double maxDiscountAmount;
                private Integer maxUsageCount;
            }

            @Data
            public static class UsageLimit {
                @JsonProperty("per_customer")
                private Integer perCustomer;
                @JsonProperty("per_product")
                private Integer perProduct;
            }

            @Data
            public static class Schedule {
                private String startsAt;
                private String endsAt;
                private Boolean hideAfterExpiration;
            }

            @Data
            public static class Budget {
                private Double totalBudget;
                private Double dailyBudget;
                private Double usedTotalBudget;
                private Double usedDailyBudget;
            }

        }

        @Data
        public static class Metafields {
            private String key;
            private String namespace;
            private String ownerId;
        }
    }

}
