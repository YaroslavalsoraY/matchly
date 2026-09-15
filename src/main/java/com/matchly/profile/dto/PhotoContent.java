package com.matchly.profile.dto;

import java.time.Instant;

/** Байты фото и его тип для отдачи клиенту. */
public record PhotoContent(byte[] data, String contentType, Instant updatedAt) {
}
