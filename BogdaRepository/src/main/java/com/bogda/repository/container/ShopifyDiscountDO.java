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

        @Data
        public static class BasicInformation {
            private String offerName;
            private OfferType offerType;


            @Data
            public static class OfferType {
                private String category;
                private String subtype;
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

            @JsonProperty("trigger_scope")
            private TriggerScope triggerScope;
            private Discount discount;
            @JsonProperty("discount_reward")
            private List<DiscountReward> discountRewards;
            private String subtitle;
            private String labelText;
            private String badgeText;
            private Boolean selectedByDefault;
            private List<Reward> reward;

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
            public static class TriggerScope {
                @JsonProperty("quantity_scope")
                private String quantityScope;
                @JsonProperty("min_quantity")
                private Integer minQuantity;
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
