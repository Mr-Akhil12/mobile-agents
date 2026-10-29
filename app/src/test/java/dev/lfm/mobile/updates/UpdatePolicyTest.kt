package dev.lfm.mobile.updates

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val url = "https://github.com/Mr-Akhil12/mobile-agents/releases/download/prototype-v0.4.0-5/Local_Agents.apk"
    private fun manifest() = JSONObject().put("versionCode", 5).put("versionName", "0.4.0")
        .put("bytes", 35000000).put("sha256", "a".repeat(64)).put("signerSha256", "b".repeat(64))
    @Test fun acceptsPinnedRepositoryRelease() {
        val value = UpdatePolicy.parse(manifest(), url, "Prototype")
        assertEquals(5L, value.code); assertEquals(35000000L, value.bytes)
    }
    @Test fun rejectsForeignHostsRepositoriesAndInsecureTransport() {
        for (bad in listOf(url.replace("https:", "http:"), url.replace("github.com", "github.com.evil.test"),
            url.replace("Mr-Akhil12", "attacker"), url.replace("github.com", "github.com:8443"), url.replace("https://", "https://user@"),
            "https://github.com/Mr-Akhil12/mobile-agents/releases/download/../../other.apk")) {
            assertTrue(runCatching { UpdatePolicy.parse(manifest(), bad, "") }.isFailure)
        }
    }
    @Test fun rejectsMalformedAndOversizedMetadata() {
        for ((field, value) in listOf("bytes" to 0L, "bytes" to UpdatePolicy.MAX_BYTES + 1,
            "versionCode" to 0L, "versionCode" to Long.MAX_VALUE, "sha256" to "bad", "signerSha256" to "")) {
            assertTrue(runCatching { UpdatePolicy.parse(manifest().put(field, value), url, "") }.isFailure)
        }
    }
}
