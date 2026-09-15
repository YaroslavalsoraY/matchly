package com.matchly.reaction;

import com.matchly.common.entity.BaseEntity;
import com.matchly.common.exception.BusinessRuleException;
import com.matchly.profile.Profile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Реакция одной анкеты на другую. Это и есть «оценка» (Rating) из постановки задачи:
 * матрица лайков - вход для коллаборативной фильтрации.
 */
@Entity
@Table(name = "reactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_profile_id", nullable = false)
    private Profile source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_profile_id", nullable = false)
    private Profile target;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 10)
    private ReactionType type;

    public Reaction(Profile source, Profile target, ReactionType type) {
        if (source.getId() != null && source.getId().equals(target.getId())) {
            throw new BusinessRuleException("You cannot react to your own profile");
        }
        this.source = source;
        this.target = target;
        this.type = type;
    }

    public void change(ReactionType newType) {
        this.type = newType;
    }

    public boolean isLike() {
        return type == ReactionType.LIKE;
    }
}
