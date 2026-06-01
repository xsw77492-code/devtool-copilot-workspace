package com.devtoolcopilot.common.web;

import jakarta.servlet.http.HttpServletRequest;

public class ClientIpUtils {
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            if (parts.length > 0) {
                String ip = parts[0].trim();
                if (!ip.isBlank()) return ip;
            }
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        return request.getRemoteAddr();
    }
}
