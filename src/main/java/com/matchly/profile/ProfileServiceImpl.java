package com.matchly.profile;

import com.matchly.common.exception.BusinessRuleException;
import com.matchly.common.exception.NotFoundException;
import com.matchly.common.util.ImageTypeDetector;
import com.matchly.interest.Interest;
import com.matchly.interest.InterestRepository;
import com.matchly.profile.dto.PhotoContent;
import com.matchly.profile.dto.ProfileCardResponse;
import com.matchly.profile.dto.ProfileRequest;
import com.matchly.profile.dto.ProfileResponse;
import com.matchly.user.User;
import com.matchly.user.UserRepository;
import com.matchly.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    static final long MAX_PHOTO_BYTES = 2L * 1024 * 1024;

    private final ProfileRepository profileRepository;
    private final ProfilePhotoRepository photoRepository;
    private final InterestRepository interestRepository;
    private final UserRepository userRepository;
    private final ProfileMapper mapper;
    private final ProfileCardAssembler cardAssembler;
    private final ImageTypeDetector imageTypeDetector;

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getOwn(Long userId) {
        Profile profile = requireOwn(userId);
        return mapper.toResponse(profile, photoMeta(profile));
    }

    @Override
    @Transactional
    public UpsertResult saveOwn(Long userId, ProfileRequest request) {
        Set<Interest> interests = resolveInterests(request.interestIds());
        Optional<Profile> existing = profileRepository.findWithInterestsByUserId(userId);
        boolean created = existing.isEmpty();
        Profile profile = existing.orElseGet(() -> new Profile(requireUser(userId)));

        profile.update(mapper.toDetails(request), interests, LocalDate.now());
        profile = profileRepository.save(profile);

        log.info("Profile {} for user id={}: profileId={}", created ? "created" : "updated", userId, profile.getId());
        return new UpsertResult(mapper.toResponse(profile, photoMeta(profile)), created);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileCardResponse getCard(Long profileId) {
        Profile profile = profileRepository.findVisibleById(profileId, UserStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Profile", profileId));
        return cardAssembler.toCard(profile);
    }

    @Override
    @Transactional
    public ProfileResponse uploadPhoto(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Photo file is empty");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new BusinessRuleException("Photo must not exceed 2 MB");
        }
        byte[] bytes = readBytes(file);
        String contentType = imageTypeDetector.detect(bytes)
                .orElseThrow(() -> new BusinessRuleException("Only JPEG, PNG or WebP images are allowed"));

        Profile profile = requireOwn(userId);
        ProfilePhoto photo = photoRepository.findByProfileId(profile.getId())
                .map(existing -> {
                    existing.replace(contentType, bytes);
                    return existing;
                })
                .orElseGet(() -> new ProfilePhoto(profile, contentType, bytes));
        photo = photoRepository.saveAndFlush(photo);

        log.info("Photo uploaded for profileId={}: {} bytes, {}", profile.getId(), bytes.length, contentType);
        return mapper.toResponse(profile, new PhotoMeta(profile.getId(), photo.getUpdatedAt()));
    }

    @Override
    @Transactional
    public ProfileResponse deletePhoto(Long userId) {
        Profile profile = requireOwn(userId);
        photoRepository.findByProfileId(profile.getId()).ifPresent(photo -> {
            photoRepository.delete(photo);
            log.info("Photo deleted for profileId={}", profile.getId());
        });
        return mapper.toResponse(profile, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PhotoContent getPhoto(Long profileId) {
        ProfilePhoto photo = photoRepository.findByProfileId(profileId)
                .orElseThrow(() -> new NotFoundException("Photo for profile " + profileId + " not found"));
        return new PhotoContent(photo.getData(), photo.getContentType(), photo.getUpdatedAt());
    }

    private Profile requireOwn(Long userId) {
        return profileRepository.findWithInterestsByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }

    private Set<Interest> resolveInterests(Set<Long> ids) {
        List<Interest> found = interestRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new BusinessRuleException("Some of the selected interests do not exist");
        }
        return new HashSet<>(found);
    }

    private PhotoMeta photoMeta(Profile profile) {
        return photoRepository.findMetaByProfileId(profile.getId()).orElse(null);
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("Cannot read uploaded file");
        }
    }
}
