package com.operaciones.backend.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppUrlProperties {

    private final String frontendBaseUrl;

    public AppUrlProperties(
            @Value("${app.frontend-base-url:https://finan.mtasoftware.cloud}") String frontendBaseUrl) {
        String raw = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
        if (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        this.frontendBaseUrl = raw.isBlank() ? "https://finan.mtasoftware.cloud" : raw;
    }

    public String frontendBaseUrl() {
        return frontendBaseUrl;
    }

    public String absoluteFrontend(String path) {
        String p = path == null ? "" : path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return frontendBaseUrl + p;
    }
}
