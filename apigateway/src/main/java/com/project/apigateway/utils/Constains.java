package com.project.apigateway.utils;

public interface Constains {
    public static interface PATH_SERVICE {
        String CUSTOMER_SERVICE = "/api/customer";
        String BUSINESS_SERVICE = "/api/business";
        String CATEGORY_SERVICE = "/api/category";
        String AUTH_SERVICE = "/api/auth";
    }

    String CATEGORY_SERVICE = "category-service";
    String CUSTOMER_SERVICE = "customer-service";
    String BUSINESS_SERVICE = "business-service";
    String AUTH_SERVICE = "auth-service";

    String JWT_TOKEN_HEADER = "Authorization";
    String JWT_TOKEN_PREFIX = "Bearer ";
    String JWT_TOKEN_SECRET_KEY = "secret-key";
    long JWT_TOKEN_EXPIRATION_TIME = 86400000; // 1 day in milliseconds
}
