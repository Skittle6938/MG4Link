package com.andrerinas.openheadunit.ota;

import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Where update traffic may go. The update is installed as the system user, so every hop of every
 * update request is checked, redirects included: HTTPS on a GitHub host, and the first hop is this
 * repository's release list or one of its release assets. A release JSON that names another host,
 * or a redirect to one, is refused rather than followed.
 */
final class OtaUrls {

    static final String REPO = "Skittle6938/MG4Link";
    static final String RELEASES_API = "https://api.github.com/repos/" + REPO + "/releases?per_page=20";

    private static final String ASSET_PATH_PREFIX = "/" + REPO + "/releases/download/";

    /** github.com serves the asset URL and redirects it to one of the two file hosts. */
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com"
    ));

    private OtaUrls() {
    }

    /** Any hop: HTTPS, default port, no credentials in the URL, on a GitHub host. */
    static boolean isAllowedHop(URL url) {
        if (url == null || !"https".equalsIgnoreCase(url.getProtocol())) return false;
        if (url.getUserInfo() != null) return false;
        if (url.getPort() != -1 && url.getPort() != 443) return false;
        String host = url.getHost();
        return host != null && HOSTS.contains(host.toLowerCase(Locale.US));
    }

    /** The first hop: the release list, or a release asset of this repository. */
    static boolean isAllowedStart(URL url) {
        if (!isAllowedHop(url)) return false;
        if (RELEASES_API.equals(url.toString())) return true;
        String path = url.getPath();
        return "github.com".equalsIgnoreCase(url.getHost())
                && path != null
                && path.startsWith(ASSET_PATH_PREFIX)
                && !path.contains("..")
                && !path.contains("%");
    }
}
