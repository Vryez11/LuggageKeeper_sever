package com.luggagekeeper.keeper_app.store.domain;

/**
 * 사업자 유형 열거형
 * Flutter의 BusinessType과 호환
 */
public enum BusinessType {
    INDIVIDUAL("개인사업자"),
    CORPORATION("법인사업자"),
    FRANCHISE("프랜차이즈"),
    OTHER("기타");  // Flutter와 호환을 위해 추가

    private final String description;

    BusinessType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}