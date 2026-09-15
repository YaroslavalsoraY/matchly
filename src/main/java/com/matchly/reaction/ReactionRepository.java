package com.matchly.reaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findBySource_IdAndTarget_Id(Long sourceId, Long targetId);

    boolean existsBySource_IdAndTarget_IdAndType(Long sourceId, Long targetId, ReactionType type);

    /** Анкеты, на которые пользователь уже отреагировал (их не нужно показывать снова). */
    @Query("select r.target.id from Reaction r where r.source.id = :sourceId")
    Set<Long> findTargetIdsBySourceId(@Param("sourceId") Long sourceId);

    /** Все лайки в системе: матрица «кто кого лайкнул». */
    @Query("select new com.matchly.reaction.LikeEdge(r.source.id, r.target.id) from Reaction r where r.type = :type")
    List<LikeEdge> findEdgesByType(@Param("type") ReactionType type);

    /** Количество полученных лайков по анкетам. */
    @Query("select new com.matchly.reaction.LikeCount(r.target.id, count(r)) from Reaction r "
            + "where r.type = :type group by r.target.id")
    List<LikeCount> countByTargetGroupedByType(@Param("type") ReactionType type);

    long countByType(ReactionType type);

    long countByTarget_IdAndType(Long targetId, ReactionType type);
}
