package com.matchly.admin;

import com.matchly.admin.dto.AdminStatsResponse;

/** Сбор статистики по всем модулям для администратора. */
public interface AdminStatsService {

    AdminStatsResponse collect();
}
