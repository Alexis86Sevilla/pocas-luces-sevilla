package com.pocasluces.backend.controller.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbedUrlValidationTest {

    @Test
    void shouldAllowKnownYouTubeAndInstagramHttpsHosts() {
        assertThat(EmbedUrlValidation.isAllowed("https://www.youtube.com/embed/abc123")).isTrue();
        assertThat(EmbedUrlValidation.isAllowed("https://youtube.com/embed/abc123")).isTrue();
        assertThat(EmbedUrlValidation.isAllowed("https://www.youtube-nocookie.com/embed/abc123")).isTrue();
        assertThat(EmbedUrlValidation.isAllowed("https://youtube-nocookie.com/embed/abc123")).isTrue();
        assertThat(EmbedUrlValidation.isAllowed("https://www.instagram.com/reel/abc123/")).isTrue();
        assertThat(EmbedUrlValidation.isAllowed("https://instagram.com/reel/abc123/")).isTrue();
    }

    @Test
    void shouldRejectNonHttpsScheme() {
        assertThat(EmbedUrlValidation.isAllowed("http://www.youtube.com/embed/abc123")).isFalse();
        assertThat(EmbedUrlValidation.isAllowed("javascript:alert(1)")).isFalse();
    }

    @Test
    void shouldRejectDisallowedHosts() {
        assertThat(EmbedUrlValidation.isAllowed("https://evil.com/embed/abc123")).isFalse();
        assertThat(EmbedUrlValidation.isAllowed("https://youtube.com.evil.com/embed/abc123")).isFalse();
        assertThat(EmbedUrlValidation.isAllowed("https://notyoutube.com")).isFalse();
    }

    @Test
    void shouldRejectNullBlankOrMalformedUrls() {
        assertThat(EmbedUrlValidation.isAllowed(null)).isFalse();
        assertThat(EmbedUrlValidation.isAllowed("")).isFalse();
        assertThat(EmbedUrlValidation.isAllowed("   ")).isFalse();
        assertThat(EmbedUrlValidation.isAllowed("not a url")).isFalse();
    }
}
