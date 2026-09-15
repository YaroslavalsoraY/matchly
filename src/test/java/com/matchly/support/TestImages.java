package com.matchly.support;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Генерация маленьких настоящих изображений для тестов загрузки фото. */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png() {
        return encode("png");
    }

    public static byte[] jpeg() {
        return encode("jpg");
    }

    private static byte[] encode(String format) {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, 0xFF3366);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
