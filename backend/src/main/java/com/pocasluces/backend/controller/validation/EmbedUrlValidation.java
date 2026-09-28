package com.pocasluces.backend.controller.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Server-side allowlist for testimonial embed URLs: only https links to a known
 * YouTube/Instagram host are considered safe to expose to clients (which will embed
 * them in an iframe or trust them via Angular's DomSanitizer).
 */
public final class EmbedUrlValidation {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
        "youtube.com",
        "www.youtube.com",
        "youtube-nocookie.com",
        "www.youtube-nocookie.com",
        "instagram.com",
        "www.instagram.com"
    );

    private EmbedUrlValidation() {
    }

    public static boolean isAllowed(String embedUrl) {
        if (embedUrl == null || embedUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(embedUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return "https".equalsIgnoreCase(scheme)
                && host != null
                && ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
