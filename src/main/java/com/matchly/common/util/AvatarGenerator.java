package com.matchly.common.util;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Генерирует SVG-аватар: градиентный фон, цвет которого определяется именем, и первая буква имени.
 * Используется для демо-анкет, чтобы не зависеть от внешних картинок.
 */
@Component
public class AvatarGenerator {

    public static final String CONTENT_TYPE = "image/svg+xml";
    private static final int SIZE = 320;

    public byte[] generate(String name, long seed) {
        int hue = (int) Math.floorMod(seed * 47 + name.hashCode(), 360);
        int hue2 = (hue + 40) % 360;
        String initial = name.isBlank() ? "?" : escape(name.substring(0, 1).toUpperCase());
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="%1$d" height="%1$d" viewBox="0 0 %1$d %1$d">
                  <defs>
                    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%%" stop-color="hsl(%2$d, 70%%, 55%%)"/>
                      <stop offset="100%%" stop-color="hsl(%3$d, 75%%, 40%%)"/>
                    </linearGradient>
                  </defs>
                  <rect width="%1$d" height="%1$d" fill="url(#g)"/>
                  <circle cx="%4$d" cy="%5$d" r="%6$d" fill="rgba(255,255,255,0.18)"/>
                  <circle cx="%7$d" cy="%8$d" r="%9$d" fill="rgba(255,255,255,0.12)"/>
                  <text x="50%%" y="54%%" text-anchor="middle" dominant-baseline="middle"
                        font-family="Segoe UI, Arial, sans-serif" font-size="%10$d" font-weight="700" fill="#ffffff">%11$s</text>
                </svg>
                """.formatted(SIZE, hue, hue2,
                (int) (SIZE * 0.78), (int) (SIZE * 0.22), (int) (SIZE * 0.28),
                (int) (SIZE * 0.2), (int) (SIZE * 0.85), (int) (SIZE * 0.22),
                (int) (SIZE * 0.42), initial);
        return svg.getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
