# TicketBAI Java

Paquete Java 11+ para emitir facturas con **TicketBAI**, el sistema de las Haciendas Forales de Álava (Araba), Bizkaia (Batuz) y Gipuzkoa. Es el equivalente de [`com.squareetlabs:verifactu`](https://github.com/squareetlabs/verifactu-sdk) para los territorios forales.

> [!WARNING]
> **En desarrollo (0.1.0-SNAPSHOT).** La API puede cambiar. No usar en producción.

## Estado

| Pieza | Estado |
|---|---|
| CRC-8 (Anexo V) | ✅ Con los vectores oficiales |
| Código TicketBAI de 39 caracteres | ✅ |
| URL del QR por territorio y entorno | ✅ Con el ejemplo oficial de Gipuzkoa |
| Territorios: política de firma y URL del QR | ✅ |
| Modelo de factura y XML (XSD 1.2.2) | Pendiente |
| Firma XAdES-EPES enveloped con la política del territorio | Pendiente |
| Encadenamiento (100 primeros caracteres de la firma anterior) | Pendiente |
| Validación local (cuadres, claves, campos por territorio) | Pendiente |
| Envío REST a Gipuzkoa y Álava (alta, anulación, Zuzendu) | Pendiente |
| Envío por lotes al LROE de Bizkaia (modelos 240/140) | Pendiente |

## Uso (lo disponible hoy)

```java
TbaiIdentifier id = TbaiIdentifier.of("B12345678", LocalDate.of(2026, 10, 18), signatureValue);

String qrUrl = QrUrlBuilder.build(Territory.GIPUZKOA, Environment.PRODUCTION, id,
        "F2026", "000123", new BigDecimal("1210.00"));
```

## Compilar

```bash
./mvnw test
```

Se compila con `--release 11`; hace falta un JDK 11 o superior.

## Fuentes

- Gipuzkoa: [documentación y normativa TicketBAI](https://www.gipuzkoa.eus/es/web/ogasuna/ticketbai/documentacion-y-normativa).
- Álava: [documentación técnica](https://web.araba.eus/es/hacienda/ticketbai/documentacion-tecnica).
- Bizkaia: [documentación técnica de Batuz](https://www.batuz.eus/es/documentacion-tecnica).

## Licencia

MIT. Ver [LICENSE](LICENSE).
