package com.matchly.interest;

/** Сколько анкет выбрали интерес (для статистики администратора). */
public record InterestUsage(String name, long profiles) {
}
