# Raíces de confianza incluidas

Las tres Diputaciones cambian la cadena TLS de sus servidores TicketBAI a Sectigo (preproducción desde el 01/10/2026; producción entre noviembre de 2026 y enero de 2027, Bizkaia el 24/11/2026). Muchos JDK no traen estas raíces, así que el SDK las añade por defecto a las del JDK (`TlsConfig`).

| Fichero | Certificado | SHA-256 |
|---|---|---|
| `sectigo-public-server-authentication-root-r46.pem` | Sectigo Public Server Authentication Root R46 (2021–2046) | `7BB647A62AEEAC88BF257AA522D01FFEA395E0AB45C73F93F65654EC38F25A06` |
| `sectigo-public-server-authentication-root-e46.pem` | Sectigo Public Server Authentication Root E46 (2021–2046) | `C90F26F0FB1B4018B22227519B5CA2B53E2CA5B3BE5CF18EFE1BEF47380C5383` |

Origen: `crt.sectigo.com` (18/10/2026). Huellas comprobadas contra el almacén de raíces del sistema de macOS.
