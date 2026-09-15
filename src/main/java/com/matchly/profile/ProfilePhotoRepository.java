package com.matchly.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProfilePhotoRepository extends JpaRepository<ProfilePhoto, Long> {

    Optional<ProfilePhoto> findByProfileId(Long profileId);

    @Query("select new com.matchly.profile.PhotoMeta(p.profile.id, p.updatedAt) from ProfilePhoto p where p.profile.id = :profileId")
    Optional<PhotoMeta> findMetaByProfileId(@Param("profileId") Long profileId);

    @Query("select new com.matchly.profile.PhotoMeta(p.profile.id, p.updatedAt) from ProfilePhoto p where p.profile.id in :profileIds")
    List<PhotoMeta> findMetaByProfileIdIn(@Param("profileIds") Collection<Long> profileIds);
}
