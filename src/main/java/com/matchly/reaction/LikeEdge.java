package com.matchly.reaction;

/** Ребро «кто кого лайкнул» для построения матрицы предпочтений. */
public record LikeEdge(Long sourceProfileId, Long targetProfileId) {
}
