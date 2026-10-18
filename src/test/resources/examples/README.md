# Ejemplos oficiales

Ficheros firmados publicados por las Diputaciones. Se guardan **byte a byte** (sin normalizar finales de línea): la firma depende de ellos.

| Carpeta | Origen | Contenido |
|---|---|---|
| `gipuzkoa/` | ZIP «Esquemas Alta y Anulación 1.2.2» de la Diputación Foral de Gipuzkoa (gipuzkoa.eus) | Primera factura, factura sucesiva encadenada y anulación (FakturaBAI, licencia de pruebas) |
| `araba/` | ZIP «Ejemplos xmls – politicaNueva» de la Diputación Foral de Álava (web.araba.eus) | Factura, rectificativa y anulación, con la respuesta de Hacienda de cada una |

**Limitaciones conocidas:**

- Los ejemplos de Gipuzkoa se reformatearon (sangrado con tabuladores) después de firmarlos: su firma ya no valida. Además, la firma encadenada en la factura 2 no es la de la factura 1 publicada.
- Los ejemplos de Álava validan, pero se firmaron con la política anterior a la especificación de 30/05/2022: su `SigPolicyHash` no es el publicado hoy. Las respuestas son de 2021 y no corresponden a la firma actual de los ficheros.
