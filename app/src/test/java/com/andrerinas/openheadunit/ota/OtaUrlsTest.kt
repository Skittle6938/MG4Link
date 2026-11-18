package com.andrerinas.openheadunit.ota

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class OtaUrlsTest {

    private val asset = "https://github.com/Skittle6938/MG4Link/releases/download/v2.2.7-beta21/MG4Link_2.2.7-beta21.apk"

    @Test
    fun `the release list and this repository's assets may start a request`() {
        assertTrue(OtaUrls.isAllowedStart(URL(OtaUrls.RELEASES_API)))
        assertTrue(OtaUrls.isAllowedStart(URL(asset)))
        assertTrue(OtaUrls.isAllowedStart(URL("$asset.sha256")))
    }

    @Test
    fun `another repository, host or scheme may not start a request`() {
        assertFalse(OtaUrls.isAllowedStart(URL("https://github.com/someone/MG4Link/releases/download/v1/x.apk")))
        assertFalse(OtaUrls.isAllowedStart(URL("https://github.com/Skittle6938/MG4Link/archive/main.zip")))
        assertFalse(OtaUrls.isAllowedStart(URL("https://evil.example/Skittle6938/MG4Link/releases/download/v1/x.apk")))
        assertFalse(OtaUrls.isAllowedStart(URL(asset.replace("https:", "http:"))))
        assertFalse(OtaUrls.isAllowedStart(URL("https://api.github.com/repos/someone/MG4Link/releases?per_page=20")))
    }

    @Test
    fun `a path that climbs out of the release folder is refused`() {
        assertFalse(OtaUrls.isAllowedStart(URL("https://github.com/Skittle6938/MG4Link/releases/download/../../someone/x.apk")))
        assertFalse(OtaUrls.isAllowedStart(URL("https://github.com/Skittle6938/MG4Link/releases/download/%2e%2e/x.apk")))
    }

    @Test
    fun `redirects may only land on GitHub's file hosts over https`() {
        assertTrue(OtaUrls.isAllowedHop(URL("https://release-assets.githubusercontent.com/github-production-release-asset/1/2?sig=x")))
        assertTrue(OtaUrls.isAllowedHop(URL("https://objects.githubusercontent.com/github-production-release-asset-2e65be/1")))
        assertFalse(OtaUrls.isAllowedHop(URL("http://release-assets.githubusercontent.com/x")))
        assertFalse(OtaUrls.isAllowedHop(URL("https://release-assets.githubusercontent.com.evil.example/x")))
        assertFalse(OtaUrls.isAllowedHop(URL("https://evil.example/x.apk")))
    }

    @Test
    fun `credentials and odd ports are refused even on a GitHub host`() {
        assertFalse(OtaUrls.isAllowedHop(URL("https://user@github.com/x")))
        assertFalse(OtaUrls.isAllowedHop(URL("https://github.com:8443/x")))
        assertTrue(OtaUrls.isAllowedHop(URL("https://github.com:443/x")))
    }
}
