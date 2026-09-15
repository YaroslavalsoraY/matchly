package com.matchly.matching;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    Optional<Match> findByProfileA_IdAndProfileB_Id(Long profileAId, Long profileBId);

    @EntityGraph(attributePaths = {"profileA", "profileA.interests", "profileB", "profileB.interests"})
    @Query("select m from Match m where m.profileA.id = :profileId or m.profileB.id = :profileId order by m.createdAt desc")
    List<Match> findAllForProfile(@Param("profileId") Long profileId);

    @EntityGraph(attributePaths = {"profileA", "profileA.interests", "profileB", "profileB.interests"})
    @Query("select m from Match m where m.id = :id")
    Optional<Match> findWithProfilesById(@Param("id") Long id);
}
