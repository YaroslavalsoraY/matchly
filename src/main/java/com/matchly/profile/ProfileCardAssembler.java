package com.matchly.profile;

import com.matchly.profile.dto.ProfileCardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Собирает карточки анкет вместе со ссылками на фото. Используется модулями матчей и рекомендаций,
 * чтобы им не приходилось знать, как хранятся фото.
 */
@Component
@RequiredArgsConstructor
public class ProfileCardAssembler {

    private final ProfilePhotoRepository photoRepository;
    private final ProfileMapper mapper;

    public ProfileCardResponse toCard(Profile profile) {
        return mapper.toCard(profile, photoRepository.findMetaByProfileId(profile.getId()).orElse(null));
    }

    /** Карточки в исходном порядке; сведения о фото загружаются одним запросом. */
    public List<ProfileCardResponse> toCards(List<Profile> profiles) {
        if (profiles.isEmpty()) {
            return List.of();
        }
        List<Long> ids = profiles.stream().map(Profile::getId).toList();
        Map<Long, PhotoMeta> photos = photoRepository.findMetaByProfileIdIn(ids).stream()
                .collect(Collectors.toMap(PhotoMeta::profileId, Function.identity()));
        return profiles.stream().map(p -> mapper.toCard(p, photos.get(p.getId()))).toList();
    }
}
