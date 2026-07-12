package com.contentfilter.core.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebMediaCatalogTest {
    @Test
    fun `catalog recognizes dedicated image and thumbnail hosts`() {
        listOf(
            "encrypted-tbn8.gstatic.com",
            "lh3.googleusercontent.com",
            "yt3.googleusercontent.com",
            "i.ytimg.com",
            "tse4.mm.bing.net",
            "th.bing.com",
            "external-content.duckduckgo.com",
            "media.zenfs.com",
            "images.unsplash.com",
            "res.cloudinary.com",
            "cdninstagram.com",
            "pbs.twimg.com",
            "secure.gravatar.com",
        ).forEach { host ->
            assertTrue(WebMediaCatalog.isImageAssetHost(host), host)
        }
    }

    @Test
    fun `catalog keeps text and shared support hosts available`() {
        listOf(
            "google.com",
            "gstatic.com",
            "accounts.googleusercontent.com",
            "bing.com",
            "s.yimg.com",
            "example.com",
        ).forEach { host ->
            assertFalse(WebMediaCatalog.isImageAssetHost(host), host)
        }
    }

    @Test
    fun `catalog identifies image and video search views for supported engines`() {
        assertTrue(WebMediaCatalog.isMediaSearchView("google.com", "/search", "q=x&tbm=isch"))
        assertTrue(WebMediaCatalog.isMediaSearchView("google.com", "/search", "q=x&udm=7"))
        assertTrue(WebMediaCatalog.isMediaSearchView("bing.com", "/images/search", "q=x"))
        assertTrue(WebMediaCatalog.isMediaSearchView("images.search.yahoo.com", "/search/images", "p=x"))
        assertTrue(WebMediaCatalog.isMediaSearchView("duckduckgo.com", "/", "q=x&iax=images&ia=images"))
    }

    @Test
    fun `ordinary result pages do not become media views`() {
        assertFalse(WebMediaCatalog.isMediaSearchView("google.com", "/search", "q=x"))
        assertFalse(WebMediaCatalog.isMediaSearchView("bing.com", "/search", "q=x"))
        assertFalse(WebMediaCatalog.isMediaSearchView("search.yahoo.com", "/search", "p=x"))
        assertFalse(WebMediaCatalog.isMediaSearchView("duckduckgo.com", "/", "q=x&ia=web"))
    }
}
