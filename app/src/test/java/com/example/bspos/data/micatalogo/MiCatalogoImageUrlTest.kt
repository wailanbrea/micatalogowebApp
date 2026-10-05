package com.example.bspos.data.micatalogo

import org.junit.Assert.assertEquals
import org.junit.Test

class MiCatalogoImageUrlTest {
    @Test
    fun relativeImageUrlsAreResolvedAgainstTheConfiguredApiHost() {
        val base = "https://micatalogo.example.test/"

        assertEquals(
            "https://micatalogo.example.test/storage/products/a.webp",
            MiCatalogoImageUrlResolver.resolve(base, "/storage/products/a.webp")
        )
        assertEquals(
            "https://micatalogo.example.test/storage/products/b.webp",
            MiCatalogoImageUrlResolver.resolve(base, "storage/products/b.webp")
        )
        assertEquals(
            "https://cdn.example.test/c.webp",
            MiCatalogoImageUrlResolver.resolve(base, "//cdn.example.test/c.webp")
        )
        assertEquals(
            "https://micatalogo.example.test/storage/products/legacy.webp",
            MiCatalogoImageUrlResolver.resolve(base, "http://micatalogo.example.test/storage/products/legacy.webp")
        )
    }
}
