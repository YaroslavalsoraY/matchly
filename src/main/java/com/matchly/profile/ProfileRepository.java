package com.matchly.profile;

import com.matchly.interest.InterestUsage;
import com.matchly.user.UserStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, Long> {

    boolean existsByUserId(Long userId);

    long countByVisibleTrue();

    /** Самые популярные интересы среди анкет. */
    @Query("select new com.matchly.interest.InterestUsage(i.name, count(p)) from Profile p join p.interests i "
            + "group by i.id, i.name order by count(p) desc, i.name asc")
    List<InterestUsage> countProfilesPerInterest(Pageable pageable);

    Optional<Profile> findByUserId(Long userId);

    @EntityGraph(attributePaths = "interests")
    Optional<Profile> findWithInterestsByUserId(Long userId);

    /** Все анкеты, доступные для показа: не скрытые, с незаблокированным владельцем. */
    @EntityGraph(attributePaths = "interests")
    @Query("select p from Profile p where p.visible = true and p.user.status = :status")
    List<Profile> findAllVisible(@Param("status") UserStatus status);

    /** Анкета, которую можно показать другим: существует, не скрыта, владелец не заблокирован. */
    @EntityGraph(attributePaths = "interests")
    @Query("select p from Profile p where p.id = :id and p.visible = true and p.user.status = :status")
    Optional<Profile> findVisibleById(@Param("id") Long id, @Param("status") UserStatus status);
}
