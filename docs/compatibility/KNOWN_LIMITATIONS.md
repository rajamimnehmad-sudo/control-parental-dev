# Limitaciones conocidas de laboratorio

- Un emulador no reproduce políticas OEM de batería, autoinicio, Settings, instalador o firmware.
- VPN virtual permite smoke del ciclo de vida, pero no certifica persistencia, siempre-activa, lockdown, handover de red ni evasiones en hardware real.
- Accessibility y Device Admin pueden habilitarse por shell en algunos entornos, pero eso no representa consentimiento, Ajustes restringidos ni restauración OEM.
- Instalación limpia es automatizable. Actualización in-place solo aporta evidencia si firma, paquete, build anterior y datos de prueba representan la distribución real.
- Desinstalación, protección, recuperación y pantallas del instalador varían por OEM; automatizarlas puede exigir debilitar la barrera y por eso quedan físicas.
- Batería, thermal throttling, memoria bajo presión, reinicio y uso prolongado no se certifican con una ejecución corta virtual.
- Notificaciones FCM reales requieren infraestructura/credenciales; el smoke de compatibilidad usa payloads o fakes locales.
- La variante `compatibility` existe solo para instrumentación y agrega `x86_64` a Usuario. No es publicable ni sustituye la validación ARM.
- Firebase Test Lab puede retirar modelos/versiones o marcarlos inestables. Los scripts consultan el catálogo y fallan antes de ejecutar si una celda no está disponible.
- La ausencia de crash no prueba protección funcional. VPN, Accessibility, Device Admin y desinstalación solo cambian a `certificado` con Nivel 3.
