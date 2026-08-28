package com.contentfilter.user.chromedataplane

import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

internal enum class ChromePreRenderShieldProfile(
    val wireName: String,
) {
    Compatible("compatible"),
    Strict("strict"),
}

internal data class ChromePreRenderTransformedDocument(
    val bytes: ByteArray,
    val headers: List<ChromeHttpHeader>,
    val documentSequence: Long,
    val nonceDigest: String,
    val readyTokenDigest: String,
    val profile: ChromePreRenderShieldProfile,
)

/**
 * Bounded DEV proof of parser-first transformation. It is intentionally not wired to real-web
 * delivery unless the feasibility gate survives the hostile fixture.
 */
internal class ChromePreRenderDocumentTransformer(
    private val randomBytes: (Int) -> ByteArray = ::secureRandomBytes,
) {
    private val documentSequence = AtomicLong()

    fun transform(
        sourceBytes: ByteArray,
        sourceHeaders: List<ChromeHttpHeader>,
        profile: ChromePreRenderShieldProfile,
    ): ChromePreRenderTransformedDocument {
        require(sourceBytes.size <= MaximumProofDocumentBytes) { "h18_document_too_large" }
        val source = sourceBytes.toString(Charsets.UTF_8)
        require(source.toByteArray(Charsets.UTF_8).contentEquals(sourceBytes)) { "h18_document_not_utf8" }
        require('\u0000' !in source) { "h18_document_contains_nul" }

        val head = HeadPattern.find(source) ?: error("h18_safe_head_missing")
        require(head.range.last < MaximumInsertionPrefixCharacters) { "h18_insertion_prefix_too_large" }
        val prefix = source.substring(0, head.range.last + 1)
        val normalizedPrefix = prefix.lowercase(Locale.US)
        require(DoctypePattern.containsMatchIn(normalizedPrefix)) { "h18_html_doctype_missing" }
        require(ForbiddenPrefixTokens.none(normalizedPrefix::contains)) { "h18_executable_before_shield" }

        val sequence = documentSequence.incrementAndGet()
        val nonce = randomToken()
        val readyToken = randomToken()
        val injection = injection(profile, sequence, nonce, readyToken)
        val transformed = source.substring(0, head.range.last + 1) + injection + source.substring(head.range.last + 1)
        return ChromePreRenderTransformedDocument(
            bytes = transformed.toByteArray(Charsets.UTF_8),
            headers = transformedHeaders(sourceHeaders, contentSecurityPolicy(profile, nonce)),
            documentSequence = sequence,
            nonceDigest = sha256(nonce.toByteArray(Charsets.US_ASCII)),
            readyTokenDigest = sha256(readyToken.toByteArray(Charsets.US_ASCII)),
            profile = profile,
        )
    }

    private fun randomToken(): String {
        val bytes = randomBytes(TokenBytes)
        return try {
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun transformedHeaders(
        sourceHeaders: List<ChromeHttpHeader>,
        contentSecurityPolicy: String,
    ): List<ChromeHttpHeader> =
        ChromeHttpHeaderPolicy.downstreamResponseHeaders(sourceHeaders).filterNot { header ->
            header.name.lowercase(Locale.US) in InvalidatedDocumentHeaders
        } +
            listOf(
                ChromeHttpHeader("Content-Type", "text/html; charset=utf-8"),
                ChromeHttpHeader("Content-Security-Policy", contentSecurityPolicy),
                ChromeHttpHeader("Cache-Control", "no-store"),
                ChromeHttpHeader("X-Content-Type-Options", "nosniff"),
            )

    private fun contentSecurityPolicy(
        profile: ChromePreRenderShieldProfile,
        nonce: String,
    ): String {
        val scriptSources =
            when (profile) {
                ChromePreRenderShieldProfile.Compatible -> "'self' 'nonce-$nonce'"
                ChromePreRenderShieldProfile.Strict -> "'nonce-$nonce'"
            }
        val styleSources =
            when (profile) {
                ChromePreRenderShieldProfile.Compatible -> "'self' 'nonce-$nonce'"
                ChromePreRenderShieldProfile.Strict -> "'nonce-$nonce'"
            }
        return "default-src 'none'; base-uri 'none'; object-src 'none'; media-src 'none'; " +
            "frame-src 'none'; child-src 'none'; worker-src 'none'; font-src 'none'; manifest-src 'none'; " +
            "img-src https:; connect-src 'self'; script-src $scriptSources; script-src-attr 'none'; " +
            "style-src $styleSources; style-src-attr 'none'"
    }

    private fun injection(
        profile: ChromePreRenderShieldProfile,
        sequence: Long,
        nonce: String,
        readyToken: String,
    ): String {
        val report = "BOOT_READY:${profile.wireName.uppercase(Locale.US)}:$sequence"
        return """
            <style id="glosh-h18-shield-style" nonce="$nonce">$ShieldCss</style>
            <script nonce="$nonce">(()=>{'use strict';
            const report='$report';const readyToken='$readyToken';
            const deny=()=>{throw new DOMException('Blocked by Glosh','SecurityError')};
            const define=(owner,name,value)=>{try{Object.defineProperty(owner,name,{value,writable:false,configurable:false})}catch(_){}};
            if(self.HTMLCanvasElement)define(HTMLCanvasElement.prototype,'getContext',()=>null);
            if(self.OffscreenCanvas)define(OffscreenCanvas.prototype,'getContext',()=>null);
            if(self.URL&&URL.createObjectURL)define(URL,'createObjectURL',deny);
            if(self.createImageBitmap)define(self,'createImageBitmap',deny);
            if(self.Worker)define(self,'Worker',function(){deny()});
            if(self.SharedWorker)define(self,'SharedWorker',function(){deny()});
            if(navigator.serviceWorker&&navigator.serviceWorker.register)define(navigator.serviceWorker,'register',deny);
            const originalAttach=Element.prototype.attachShadow;
            define(Element.prototype,'attachShadow',function(init){const root=originalAttach.call(this,init);
            const style=document.createElement('style');style.textContent='$ShadowShieldCss';root.prepend(style);return root});
            const shield=document.getElementById('glosh-h18-shield-style');
            new MutationObserver(()=>{if(!document.getElementById('glosh-h18-shield-style'))document.head.prepend(shield)})
              .observe(document.documentElement,{childList:true,subtree:true});
            const host=document.createElement('span');host.id='glosh-h18-ready-host';host.className='glosh-h18-ready-host';
            const marker=originalAttach.call(host,{mode:'closed'});const value=document.createElement('span');
            value.setAttribute('role','status');value.setAttribute('aria-label','glosh-shield-ready:'+readyToken);marker.append(value);
            document.documentElement.append(host);document.documentElement.dataset.gloshH18Ready='true';
            const xhr=new XMLHttpRequest();xhr.open('POST','/web18/report',false);xhr.setRequestHeader('Content-Type','text/plain');
            try{xhr.send(report)}catch(_){}const current=document.currentScript;if(current)current.remove();})();</script>
        """.trimIndent()
    }

    private companion object {
        const val TokenBytes = 16
        const val MaximumProofDocumentBytes = 256 * 1024
        const val MaximumInsertionPrefixCharacters = 16 * 1024
        const val ShieldCss =
            "canvas,svg,video,object,embed,iframe,[srcdoc],img[src^='data:'],img[src^='blob:']" +
                "{visibility:hidden!important;opacity:0!important}" +
                ".glosh-h18-ready-host{position:fixed;left:0;top:0;width:1px;height:1px;overflow:hidden;opacity:.001}"
        const val ShadowShieldCss =
            "canvas,svg,video,object,embed,iframe,[srcdoc],img[src^='data:'],img[src^='blob:']" +
                "{visibility:hidden!important;opacity:0!important}"
        val HeadPattern = Regex("<head(?:\\s[^>]*)?>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val DoctypePattern = Regex("<!doctype\\s+html(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE)
        val ForbiddenPrefixTokens =
            listOf("<script", "<style", "<link", "<body", "<img", "<svg", "<iframe", "<object", "<embed")
        val InvalidatedDocumentHeaders =
            setOf(
                "content-type",
                "content-encoding",
                "content-length",
                "content-range",
                "etag",
                "last-modified",
                "content-md5",
                "digest",
                "accept-ranges",
                "vary",
                "cache-control",
                "expires",
                "content-security-policy",
                "content-security-policy-report-only",
            )

        fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also(SecureRandom()::nextBytes)
    }
}
