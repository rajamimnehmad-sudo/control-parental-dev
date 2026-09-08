package com.contentfilter.user.chromedataplane

/** Local text only: never includes upstream markup, URLs, or an automatic retry. */
internal object ChromeDocumentFailurePage {
    fun bytes(reason: String): ByteArray {
        val message =
            when (reason) {
                "document_status_429" -> "El sitio limitó temporalmente las solicitudes. Esperá un momento antes de volver a intentar."
                "document_status_403" -> "El sitio rechazó esta solicitud."
                "document_status_404" -> "El sitio no encontró esta página."
                "document_status_500", "document_status_502", "document_status_503", "document_status_504" ->
                    "El sitio no pudo responder en este momento. Intentá más tarde."
                else -> "Glosh no pudo mostrar esta página con la protección activa."
            }
        return (
            "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<title>No se pudo cargar la página</title></head>" +
                "<body><main><h1>No se pudo cargar la página</h1><p>$message</p>" +
                "<p>Podés volver atrás o abrir otra página. La protección sigue activa.</p></main></body></html>"
        ).toByteArray(Charsets.UTF_8)
    }
}
