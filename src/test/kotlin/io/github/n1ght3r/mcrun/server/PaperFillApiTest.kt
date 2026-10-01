package io.github.n1ght3r.mcrun.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PaperFillApiTest {
    @Test
    fun `parses the Fill v3 builds response`() {
        val json = """
            [{"id":132,"time":"2026-05-11T11:43:09Z","channel":"STABLE","commits":[],
              "downloads":{"server:default":{"name":"paper-1.21.11-132.jar",
                "checksums":{"sha256":"5ffe"},"size":54846016,
                "url":"https://fill-data.papermc.io/v1/objects/5ffe/paper-1.21.11-132.jar"}}},
             {"id":133,"channel":"BETA","downloads":{"server:default":{"name":"x.jar",
                "checksums":{"sha256":"abcd"},"url":"https://example/x.jar"}}},
             {"id":134,"channel":"STABLE","downloads":{}}]
        """.trimIndent()
        val builds = PaperFillApi.parseBuilds(json)
        assertEquals(2, builds.size)
        assertEquals(132, builds[0].id)
        assertEquals("STABLE", builds[0].channel)
        assertEquals("5ffe", builds[0].sha256)
        assertEquals("BETA", builds[1].channel)
    }
}
