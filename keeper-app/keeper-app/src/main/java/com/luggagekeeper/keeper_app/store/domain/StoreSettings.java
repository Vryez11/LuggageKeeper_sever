package com.luggagekeeper.keeper_app.store.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 매장 설정 정보 엔티티
 * Flutter의 StoreSettings 모델과 호환
 */
@Entity
@Table(name = "store_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoreSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "store_id", nullable = false)
    @NotNull
    private Store store;

    // === 기본 정보 ===
    @ElementCollection
    @CollectionTable(name = "store_photos", joinColumns = @JoinColumn(name = "store_settings_id"))
    @Column(name = "photo_url")
    private List<String> storePhotos = new ArrayList<>();

    // === 운영 설정 ===
    // 기본 운영 시간 (하위 호환성)
    @Column(name = "operating_hours_start")
    private String operatingHoursStart;

    @Column(name = "operating_hours_end")
    private String operatingHoursEnd;

    // 기본 운영 시간 (새로운 필드)
    @Column(name = "default_open_time")
    private LocalTime defaultOpenTime = LocalTime.of(9, 0);

    @Column(name = "default_close_time")
    private LocalTime defaultCloseTime = LocalTime.of(22, 0);

    // 요일별 운영 시간
    @OneToMany(mappedBy = "storeSettings", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @MapKey(name = "dayOfWeek")
    private Map<String, DailyOperatingHours> dailyOperatingHours = new HashMap<>();

    @Column(name = "total_slots")
    @PositiveOrZero
    private Integer totalSlots = 20;

    @Column(name = "daily_rate_threshold")
    @PositiveOrZero
    private Integer dailyRateThreshold = 7;

    @Column(name = "auto_approval")
    private Boolean autoApproval = false;

    @Column(name = "auto_overdue_notification")
    private Boolean autoOverdueNotification = true;

    // === 보관 설정 ===
    @Column(name = "max_storage_capacity")
    @PositiveOrZero
    private Integer maxStorageCapacity = 10;

    @OneToMany(mappedBy = "storeSettings", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<LuggagePriceSettings> luggagePriceSettings = new ArrayList<>();

    // === 알림 설정 ===
    @Column(name = "new_reservation_notification")
    private Boolean newReservationNotification = true;

    @Column(name = "checkout_reminder_notification")
    private Boolean checkoutReminderNotification = true;

    @Column(name = "overdue_notification")
    private Boolean overdueNotification = true;

    @Column(name = "system_notification")
    private Boolean systemNotification = true;

    // === 카테고리 ===
    @OneToMany(mappedBy = "storeSettings", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private List<CategoryItem> categories = new ArrayList<>();

    // === 하위 호환성 필드 ===
    @Column(name = "auto_accept_reservation")
    private Boolean autoAcceptReservation = false;

    @Column(name = "notification_enabled")
    private Boolean notificationEnabled = true;

    @Column(name = "price_per_hour")
    private Integer pricePerHour;

    @Column(name = "price_per_day")
    private Integer pricePerDay;

    // === 편의 메서드들 ===

    /**
     * 요일별 운영시간 추가
     */
    public void addDailyOperatingHours(String dayOfWeek, LocalTime openTime, LocalTime closeTime, boolean isOperating) {
        DailyOperatingHours hours = new DailyOperatingHours();
        hours.setStoreSettings(this);
        hours.setDayOfWeek(dayOfWeek);
        hours.setOpenTime(openTime);
        hours.setCloseTime(closeTime);
        hours.setIsOperating(isOperating);

        dailyOperatingHours.put(dayOfWeek, hours);
    }

    /**
     * 짐 크기별 가격 설정 추가
     */
    public void addLuggagePriceSetting(LuggagePriceSettings.LuggageSize size, int hourlyRate, int dailyRate, boolean isEnabled) {
        LuggagePriceSettings priceSetting = new LuggagePriceSettings();
        priceSetting.setStoreSettings(this);
        priceSetting.setLuggageSize(size);
        priceSetting.setHourlyRate(hourlyRate);
        priceSetting.setDailyRate(dailyRate);
        priceSetting.setIsEnabled(isEnabled);

        luggagePriceSettings.add(priceSetting);
    }

    /**
     * 카테고리 아이템 추가
     */
    public void addCategory(CategoryItem category) {
        categories.add(category);
        category.setStoreSettings(this);
        if (category.getDisplayOrder() == null) {
            category.setDisplayOrder(categories.size());
        }
    }

    /**
     * 매장 사진 추가
     */
    public void addStorePhoto(String photoUrl) {
        if (photoUrl != null && !photoUrl.trim().isEmpty()) {
            storePhotos.add(photoUrl.trim());
        }
    }

    /**
     * 특정 요일의 운영 시간 조회
     */
    public DailyOperatingHours getDailyOperatingHours(String dayOfWeek) {
        return dailyOperatingHours.get(dayOfWeek);
    }

    /**
     * 특정 크기의 짐 가격 설정 조회
     */
    public LuggagePriceSettings getLuggagePriceSetting(LuggagePriceSettings.LuggageSize size) {
        return luggagePriceSettings.stream()
                .filter(setting -> setting.getLuggageSize() == size)
                .findFirst()
                .orElse(null);
    }

    /**
     * 활성화된 카테고리들만 반환
     */
    public List<CategoryItem> getActiveCategories() {
        return categories.stream()
                .filter(CategoryItem::isActive)
                .toList();
    }

    /**
     * 활성화된 짐 크기별 가격 설정들만 반환
     */
    public List<LuggagePriceSettings> getEnabledPriceSettings() {
        return luggagePriceSettings.stream()
                .filter(LuggagePriceSettings::isEnabled)
                .toList();
    }

    /**
     * 오늘 운영 중인지 확인
     */
    public boolean isOperatingToday() {
        String today = java.time.LocalDate.now().getDayOfWeek().toString();
        DailyOperatingHours todayHours = getDailyOperatingHours(today);
        return todayHours != null && todayHours.isOperating();
    }

    /**
     * 설정 완성도 확인
     */
    public boolean isSettingsComplete() {
        return !dailyOperatingHours.isEmpty() &&
                !luggagePriceSettings.isEmpty() &&
                maxStorageCapacity != null &&
                maxStorageCapacity > 0;
    }

    /**
     * 기본 설정으로 초기화
     */
    @PostLoad
    @PostPersist
    public void initializeDefaults() {
        // 요일별 운영 시간이 없으면 기본값으로 초기화
        if (dailyOperatingHours.isEmpty()) {
            String[] days = {"월", "화", "수", "목", "금", "토", "일"};
            for (int i = 0; i < days.length; i++) {
                boolean isOperating = i < 6; // 월~토 운영, 일요일 휴무
                addDailyOperatingHours(days[i], defaultOpenTime, defaultCloseTime, isOperating);
            }
        }

        // 짐 크기별 가격이 없으면 기본값으로 초기화
        if (luggagePriceSettings.isEmpty()) {
            addLuggagePriceSetting(LuggagePriceSettings.LuggageSize.SMALL, 2000, 15000, true);
            addLuggagePriceSetting(LuggagePriceSettings.LuggageSize.MEDIUM, 3000, 24000, true);
            addLuggagePriceSetting(LuggagePriceSettings.LuggageSize.LARGE, 5000, 40000, true);
        }
    }
}