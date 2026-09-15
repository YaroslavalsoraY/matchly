package com.matchly.profile;

import com.matchly.interest.Interest;
import com.matchly.interest.dto.InterestResponse;
import com.matchly.profile.dto.ProfileCardResponse;
import com.matchly.profile.dto.ProfileRequest;
import com.matchly.profile.dto.ProfileResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Преобразования между сущностью анкеты, доменным объектом деталей и DTO. */
@Component
public class ProfileMapper {

    public ProfileDetails toDetails(ProfileRequest request) {
        return new ProfileDetails(request.displayName(), request.birthDate(), request.gender(), request.lookingFor(),
                request.ageMin(), request.ageMax(), request.city(), request.bio(), request.contact(),
                request.visibleOrDefault());
    }

    public ProfileResponse toResponse(Profile profile, PhotoMeta photo) {
        return new ProfileResponse(profile.getId(), profile.getUser().getId(), profile.getDisplayName(),
                profile.getBirthDate(), profile.age(LocalDate.now()), profile.getGender(), profile.getLookingFor(),
                profile.getAgeMin(), profile.getAgeMax(), profile.getCity(), profile.getBio(), profile.getContact(),
                profile.isVisible(), toInterests(profile.getInterests()), photoUrl(photo), profile.getUpdatedAt());
    }

    public ProfileCardResponse toCard(Profile profile, PhotoMeta photo) {
        return new ProfileCardResponse(profile.getId(), profile.getDisplayName(), profile.age(LocalDate.now()),
                profile.getGender(), profile.getCity(), profile.getBio(), toInterests(profile.getInterests()),
                photoUrl(photo));
    }

    public List<InterestResponse> toInterests(Collection<Interest> interests) {
        return interests.stream()
                .sorted(Comparator.comparing(Interest::getCategory).thenComparing(Interest::getName))
                .map(InterestResponse::from)
                .toList();
    }

    public static String photoUrl(PhotoMeta photo) {
        return photo == null ? null : photo.url();
    }
}
