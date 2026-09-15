package com.matchly.profile;

import com.matchly.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Фото анкеты, хранится прямо в базе (bytea). Отдельная сущность, чтобы тяжёлые байты
 * не загружались вместе с анкетой при построении рекомендаций.
 */
@Entity
@Table(name = "profile_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfilePhoto extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, unique = true)
    private Profile profile;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(nullable = false)
    private byte[] data;

    public ProfilePhoto(Profile profile, String contentType, byte[] data) {
        this.profile = profile;
        replace(contentType, data);
    }

    public void replace(String contentType, byte[] data) {
        this.contentType = contentType;
        this.data = data;
        this.sizeBytes = data.length;
    }
}
