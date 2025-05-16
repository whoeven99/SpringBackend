package com.bogdatech.utils;

public class LanguagePackIdUtils {
    public static String getLanguagePackIdByValue(String value) {
        switch (value) {
            case "1":
                return "General";
            case "2":
                return "Fashion & Apparel";
            case "3":
                return "Electronics & Technology";
            case "4":
                return "Home Goods & Daily Essentials";
            case "5":
                return "Pet Supplies";
            case "6":
                return "Beauty & Personal Care";
            case "7":
                return "Furniture & Gardening";
            case "8":
                return "Hardware & Tools";
            case "9":
                return "Baby & Toddler Products";
            case "10":
                return "Toys & Games";
            case "11":
                return "Luggage & Accessories";
            case "12":
                return "Health & Nutrition";
            case "13":
                return "Outdoor & Sports";
            case "14":
                return "Crafts & Small Goods";
            case "15":
                return "Home Appliances";
            case "16":
                return "Automotive Parts";
            default:
                return null;
        }
    }
}
