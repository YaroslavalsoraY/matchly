package com.matchly.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Одна настройка приложения. Ключ - естественный идентификатор. */
@Entity
@Table(name = "app_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 50)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 255)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AppSetting(String key, String value) {
        this.key = key;
        update(value);
    }

    public void update(String newValue) {
        this.value = newValue;
        this.updatedAt = Instant.now();
    }
}
