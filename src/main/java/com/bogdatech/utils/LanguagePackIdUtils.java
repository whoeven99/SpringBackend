package com.bogdatech.utils;

public class LanguagePackIdUtils {
    public static String getLanguagePackIdByValue(String value) {
        return switch (value) {
            case "2" -> "Fashion & Apparel";
            case "3" -> "Electronics & Technology";
            case "4" -> "Home Goods & Daily Essentials";
            case "5" -> "Pet Supplies";
            case "6" -> "Beauty & Personal Care";
            case "7" -> "Furniture & Gardening";
            case "8" -> "Hardware & Tools";
            case "9" -> "Baby & Toddler Products";
            case "10" -> "Toys & Games";
            case "11" -> "Luggage & Accessories";
            case "12" -> "Health & Nutrition";
            case "13" -> "Outdoor & Sports";
            case "14" -> "Crafts & Small Goods";
            case "15" -> "Home Appliances";
            case "16" -> "Automotive Parts";
            default -> null;
        };
    }
}
