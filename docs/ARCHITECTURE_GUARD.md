# ARCHITECTURE GUARD

Guardrails transversales. Las decisiones concretas vigentes se verifican contra Glosh Central, GitHub y la evidencia del frente; este archivo no reemplaza ese estado.

- Mantener separacion de responsabilidades y dependencias claras; no redisenar modulos por conveniencia local ni por una metrica artificial.
- No reabrir una arquitectura ya validada salvo nueva evidencia, regresion, requisito o limitacion que la invalide.
- `PolicyEngine` conserva la politica de negocio que le corresponde; guards especializados de transporte, contenido, seguridad o lifecycle pueden vivir en su modulo cuando esa responsabilidad no pertenece al motor de politica.
- Room sigue siendo almacenamiento local donde la arquitectura actual lo usa; no convertirlo en una regla que impida backend, cache o estado especializado justificado.
- Supabase participa en backend/sync/auth/storage/operaciones segun los contratos actuales; no asumir que su unico rol es sincronizar.
- Web/red usa la arquitectura VPN/DNS/transporte/proxy vigente segun el frente. No reducirla mecanicamente a “VPN = dominios”.
- Proteccion de apps/sistema combina las APIs oficiales apropiadas (por ejemplo Device Owner/DevicePolicyManager y Accessibility como complemento) segun el alcance; no asumir “Accessibility = todo”.
- No mover responsabilidades entre modulos ni introducir nuevas dependencias amplias sin una causa concreta, impacto revisado y tests/gates proporcionales.
