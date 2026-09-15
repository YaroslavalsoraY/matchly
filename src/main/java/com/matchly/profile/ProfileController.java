package com.matchly.profile;

import com.matchly.profile.dto.PhotoContent;
import com.matchly.profile.dto.ProfileCardResponse;
import com.matchly.profile.dto.ProfileRequest;
import com.matchly.profile.dto.ProfileResponse;
import com.matchly.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@Tag(name = "Profiles", description = "Своя анкета, карточки других анкет и фото")
@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @Operation(summary = "Моя анкета")
    @ApiResponse(responseCode = "404", description = "Анкета ещё не создана (нужен онбординг)")
    @GetMapping("/me")
    public ProfileResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return profileService.getOwn(user.id());
    }

    @Operation(summary = "Создать или обновить мою анкету", description = "201 при создании, 200 при обновлении")
    @PutMapping("/me")
    public ResponseEntity<ProfileResponse> saveMe(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody ProfileRequest request) {
        ProfileService.UpsertResult result = profileService.saveOwn(user.id(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.profile());
    }

    @Operation(summary = "Загрузить или заменить фото", description = "JPEG, PNG или WebP до 2 МБ, поле формы file")
    @PutMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse uploadPhoto(@AuthenticationPrincipal AuthenticatedUser user,
                                       @RequestPart("file") MultipartFile file) {
        return profileService.uploadPhoto(user.id(), file);
    }

    @Operation(summary = "Удалить фото")
    @DeleteMapping("/me/photo")
    public ProfileResponse deletePhoto(@AuthenticationPrincipal AuthenticatedUser user) {
        return profileService.deletePhoto(user.id());
    }

    @Operation(summary = "Карточка анкеты другого пользователя")
    @GetMapping("/{id}")
    public ProfileCardResponse card(@PathVariable Long id) {
        return profileService.getCard(id);
    }

    @Operation(summary = "Фото анкеты", description = "Ссылка содержит параметр v для инвалидации кэша при замене фото")
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        PhotoContent content = profileService.getPhoto(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .lastModified(content.updatedAt())
                .body(content.data());
    }
}
