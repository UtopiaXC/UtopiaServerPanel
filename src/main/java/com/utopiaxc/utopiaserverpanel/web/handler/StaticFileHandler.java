package com.utopiaxc.utopiaserverpanel.web.handler;

import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;

import java.io.IOException;
import java.io.InputStream;

/**
 * Serves static files from the {@code web/} classpath resources directory.
 * Supports SPA fallback: paths without file extensions fall back to {@code index.html}.
 */
public final class StaticFileHandler {
    private static final String RESOURCE_PREFIX = "web";

    private StaticFileHandler() {}

    /**
     * Attempt to serve a static file matching the request path.
     * @return {@code true} if a file was found and served, {@code false} otherwise
     */
    public static boolean tryServe(RequestContext ctx) {
        String path = ctx.path();
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        String resourcePath = RESOURCE_PREFIX + path;
        byte[] content = loadResource(resourcePath);
        boolean isSpaFallback = false;

        // SPA fallback: non-file paths serve index.html
        if (content == null && !path.contains(".")) {
            content = loadResource(RESOURCE_PREFIX + "/index.html");
            isSpaFallback = true;
        }

        if (content == null) {
            return false;
        }

        // Use the actual file path for content type, not the request path
        String contentType = isSpaFallback
                ? "text/html; charset=utf-8"
                : guessContentType(path);
        ResponseHelper.sendFile(ctx, content, contentType);
        return true;
    }

    private static byte[] loadResource(String path) {
        try (InputStream is = StaticFileHandler.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) return null;
            return is.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static String guessContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (path.endsWith(".css"))  return "text/css; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".gif"))  return "image/gif";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".ico"))  return "image/x-icon";
        if (path.endsWith(".woff"))  return "font/woff";
        if (path.endsWith(".woff2")) return "font/woff2";
        if (path.endsWith(".ttf"))   return "font/ttf";
        return "application/octet-stream";
    }
}
