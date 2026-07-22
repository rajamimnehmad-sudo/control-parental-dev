package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagCaptchaPolicyTest {
    @Test
    fun `only closed HTTPS captcha provider routes are accepted`() {
        assertTrue(isDagCaptchaProviderUrl("https://www.google.com/recaptcha/api2/anchor"))
        assertTrue(isDagCaptchaProviderUrl("https://www.recaptcha.net/recaptcha/enterprise/anchor"))
        assertTrue(
            isDagCaptchaProviderUrl(
                "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/g/turnstile/if/ov2/av0",
            ),
        )
        assertTrue(isDagCaptchaProviderUrl("https://newassets.hcaptcha.com/captcha/v1/challenge"))

        assertFalse(isDagCaptchaProviderUrl("http://www.google.com/recaptcha/api2/anchor"))
        assertFalse(isDagCaptchaProviderUrl("https://www.google.com/maps"))
        assertFalse(isDagCaptchaProviderUrl("https://www.google.com.evil.example/recaptcha/api2/anchor"))
        assertFalse(isDagCaptchaProviderUrl("https://example.com/recaptcha/api2/anchor"))
    }

    @Test
    fun `only closed captcha resources can bypass image interception during a validated session`() {
        assertTrue(isDagCaptchaSessionResourceUrl("https://www.google.com/recaptcha/api2/payload?p=token"))
        assertTrue(
            isDagCaptchaSessionResourceUrl(
                "https://www.gstatic.com/recaptcha/releases/version/styles__ltr.css",
            ),
        )
        assertTrue(isDagCaptchaSessionResourceUrl("https://newassets.hcaptcha.com/captcha/v1/challenge"))

        assertFalse(isDagCaptchaSessionResourceUrl("http://www.gstatic.com/recaptcha/image.png"))
        assertFalse(isDagCaptchaSessionResourceUrl("https://www.gstatic.com/maps/image.png"))
        assertFalse(isDagCaptchaSessionResourceUrl("https://example.com/recaptcha/image.png"))
    }
}
