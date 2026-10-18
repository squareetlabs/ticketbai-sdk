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
| XSD oficiales 1.2.2 (alta, anulación, respuesta) y validación contra ellos | ✅ Con los ejemplos firmados de Gipuzkoa y Álava |
| Modelo de factura y XML de alta | ✅ Reproduce la primera factura oficial de Gipuzkoa y la rectificativa de Álava |
| Anulación (`AnulaTicketBai`) | ✅ Reproduce la anulación oficial de Álava |
| Encadenamiento (100 primeros caracteres de la firma anterior) | ✅ |
| Firma XAdES-EPES enveloped con la política del territorio (EU DSS 5.11.1) | ✅ |
| Verificación local de la firma (API del JDK) | ✅ Verifica las firmas reales de los ejemplos de Álava |
| Fachada `TicketBai`: emitir (firma, XSD, código, QR, eslabón) y anular | ✅ |
| Validación de negocio (cuadres, claves compatibles, campos por territorio) | ✅ Reglas publicadas, con su fuente; errores bloquean la firma |
| Envío REST a Gipuzkoa y Álava (alta y anulación) con TLS mutuo | ✅ Probado contra un servidor local con TLS mutuo y las respuestas oficiales de Álava |
| Zuzendu (subsanar/modificar) | Pendiente |
| Envío al LROE de Bizkaia, modelo 240 (alta y anulación): lotes de hasta 1.000, GZIP y cabeceras `eus-bizkaia-n3` | ✅ Reconstruye el envío oficial y lee las respuestas oficiales; probado con TLS mutuo |
| LROE modelo 140 (personas físicas: lleva además los datos de renta por ingreso) | Pendiente |

## Uso

```java
Software software = new Software("TBAIGIPRE00000000001", Party.withNif("A99800005", null),
        "SOFTWARE GARANTE TICKETBAI PRUEBA", "1.0");

try (TicketBaiSigner signer = TicketBaiSigner.pkcs12(p12Bytes, password)) {
    TicketBai tbai = TicketBai.builder()
            .territory(Territory.GIPUZKOA)
            .environment(Environment.TEST)
            .software(software)
            .signer(signer)
            .build();

    Invoice invoice = Invoice.builder()
            .issuer("B12345674", "CONSTRUCCIONES DE PRUEBA SL")
            .recipient(Party.withNif("11111111H", "CLIENTE").withAddress("20001", "Calle Mayor 1"))
            .series("F2026").number("1")
            .issuedAt(LocalDate.now(), LocalTime.now())
            .description("Certificación de obra")
            .line(new InvoiceLine("Partida 1", new BigDecimal("1"), new BigDecimal("1000"), null, new BigDecimal("1210")))
            .totalAmount(new BigDecimal("1210"))
            .regimeKey(RegimeKey.GENERAL)
            .breakdown(Breakdown.ofInvoice(TaxBreakdown.builder()
                    .nonExempt(NonExemptType.S1, VatDetail.of(new BigDecimal("1000"), new BigDecimal("21"), new BigDecimal("210")))
                    .build()))
            .build();

    IssuedInvoice issued = tbai.issue(invoice, previousLink);   // previousLink: null en la primera factura del emisor
    byte[] xml = issued.getSignedXml().getBytes();              // enviar y conservar sin cambios
    String code = issued.getIdentifier().getValue();            // imprimir encima del QR
    String qr = issued.getQrUrl();                              // contenido del QR
    ChainLink next = issued.getChainLink();                     // guardar para la siguiente factura
}
```

### Envío (Gipuzkoa y Álava)

```java
TlsConfig tls = TlsConfig.builder()
        .clientCertificate(p12Bytes, password)      // certificado del obligado, representante, sello o dispositivo
        // las raíces Sectigo R46/E46 (cambio de 2026-2027) ya vienen incluidas; .trust(...) para añadir otras
        .build();
TicketBaiRestClient client = new TicketBaiRestClient(Territory.GIPUZKOA, Environment.TEST, tls);

SubmissionResult result = client.submitInvoice(issued.getSignedXml());
if (result.isReceived()) {
    // 00: guardar result.getRawResponse() junto al fichero; revisar result.getValidationResults() (avisos)
} else {
    // 01: rechazado; se corrige con Zuzendu (subsanar) sin regenerar el fichero
}
// TransportException: no hay respuesta válida; reintentar con el MISMO fichero
```

### Envío (Bizkaia, LROE)

En Bizkaia el fichero TicketBAI no se envía al expedir: se registra en el LROE dentro de su plazo (trimestral, o 4 días en un régimen tipo SII), en lotes de un solo obligado.

```java
LroeBatch batch = LroeBatch.of("B12345674", "CONSTRUCCIONES DE PRUEBA SL", Year.of(2026),
        LroeOperation.INVOICES, List.of(issued1.getSignedXml(), issued2.getSignedXml()));   // hasta 1.000
LroeResult result = new LroeClient(Environment.TEST, tls).submit(batch);
// result.getStatus(): CORRECT / PARTIALLY_CORRECT / INCORRECT; result.getRecords(): estado de cada factura
```

> [!IMPORTANT]
> El encadenamiento es responsabilidad de quien llama: hay que guardar `getChainLink()` y pasarlo a la siguiente factura del mismo emisor, en orden y sin emitir dos facturas del mismo emisor a la vez.

## Pendiente de confirmar con las Diputaciones

- **URL del QR:** el código TicketBAI lleva caracteres del Base64 de la firma (`+`, `/`). El SDK los codifica (`%2B`, `%2F`) y calcula el CRC-8 sobre la URL ya codificada, como dice el Anexo V. Hay que comprobarlo con la herramienta de pruebas del QR de Bizkaia (`pruebatuz.bizkaia.eus/B4PEQR0C/test`) y con las URL de pruebas de Gipuzkoa y Álava.
- **Política de firma de Bizkaia:** el SDK firma con la v1.2 vigente (OF 601/2025). Los ejemplos oficiales del LROE usan la v1.0; no está confirmado si Bizkaia sigue aceptando ficheros con la v1.0.
- **Tolerancia de los cuadres:** no está publicada; el validador es exacto por defecto (`InvoiceValidator.strict().withTolerance(...)` para ampliarla).

## Decisiones

- **XML con DOM, sin JAXB:** el SDK no añade dependencias de JAXB y puede convivir con `com.squareetlabs:verifactu` (JAXB 2.3) en la misma aplicación. El XSD oficial es la referencia y cada fichero firmado se valida contra él.
- **EU DSS 5.11.1:** la misma versión que el SDK de VERI\*FACTU.
- **Firma como los ficheros oficiales:** transformación `enveloped-signature` y C14N inclusiva, RSA con SHA-256 (válido en los tres territorios; Álava solo admite RSA).
- **Ejemplos oficiales:** los de Gipuzkoa se reformatearon después de firmarlos y su firma no valida; los de Álava sí validan, pero con el hash de la política anterior a 2022. Ver `src/test/resources/examples/README.md`.

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
