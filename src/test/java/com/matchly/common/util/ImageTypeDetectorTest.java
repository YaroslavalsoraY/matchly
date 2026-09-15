package com.matchly.common.util;

import com.matchly.support.TestImages;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeDetectorTest {

    private final ImageTypeDetector detector = new ImageTypeDetector();

    @Test
    void detectsPngAndJpeg() {
        assertThat(detector.detect(TestImages.png())).contains("image/png");
        assertThat(detector.detect(TestImages.jpeg())).contains("image/jpeg");
    }

    @Test
    void detectsWebpBySignature() {
        byte[] webp = new byte[16];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
        System.arraycopy("WEBPVP8 ".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 8);
        assertThat(detector.detect(webp)).contains("image/webp");
    }

    @Test
    void rejectsUnknownAndTooShortContent() {
        assertThat(detector.detect("<html>not an image</html>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(detector.detect(new byte[]{1, 2, 3})).isEmpty();
        assertThat(detector.detect(null)).isEmpty();
    }
}
