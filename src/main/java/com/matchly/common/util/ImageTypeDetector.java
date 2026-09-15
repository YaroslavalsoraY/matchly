package com.matchly.common.util;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Определяет тип изображения по сигнатуре файла, а не по заголовку Content-Type от клиента,
 * которому доверять нельзя. Поддерживаются JPEG, PNG и WebP.
 */
@Component
public class ImageTypeDetector {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    public Optional<String> detect(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return Optional.empty();
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(bytes, PNG_SIGNATURE)) {
            return Optional.of("image/png");
        }
        if (startsWith(bytes, RIFF) && Arrays.equals(Arrays.copyOfRange(bytes, 8, 12), WEBP)) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return Arrays.equals(Arrays.copyOfRange(bytes, 0, prefix.length), prefix);
    }
}
