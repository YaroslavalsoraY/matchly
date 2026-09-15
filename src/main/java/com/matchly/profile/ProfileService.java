package com.matchly.profile;

import com.matchly.profile.dto.PhotoContent;
import com.matchly.profile.dto.ProfileCardResponse;
import com.matchly.profile.dto.ProfileRequest;
import com.matchly.profile.dto.ProfileResponse;
import org.springframework.web.multipart.MultipartFile;

/** Собственная анкета пользователя, карточки других анкет и фото. */
public interface ProfileService {

    /** Своя анкета; 404, если пользователь ещё не прошёл онбординг. */
    ProfileResponse getOwn(Long userId);

    /** Создаёт анкету или обновляет существующую (идемпотентно). */
    UpsertResult saveOwn(Long userId, ProfileRequest request);

    /** Карточка чужой анкеты; скрытые и заблокированные анкеты выглядят как несуществующие. */
    ProfileCardResponse getCard(Long profileId);

    ProfileResponse uploadPhoto(Long userId, MultipartFile file);

    ProfileResponse deletePhoto(Long userId);

    PhotoContent getPhoto(Long profileId);

    /** Результат сохранения: анкета и признак, что она была создана, а не обновлена. */
    record UpsertResult(ProfileResponse profile, boolean created) {
    }
}
