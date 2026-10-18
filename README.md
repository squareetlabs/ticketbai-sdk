# TicketBAI Java

Paquete Java 11+ para emitir facturas con **TicketBAI**, el sistema de las Haciendas Forales de Álava (Araba), Bizkaia (Batuz) y Gipuzkoa. Es el equivalente de [`com.squareetlabs:verifactu`](https://github.com/squareetlabs/verifactu-sdk) para los territorios forales.

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
| Zuzendu (Gipuzkoa y Álava): subsanar o modificar un alta y subsanar una anulación, y su envío | ✅ Huella y firma del original; impide cambiar emisor, serie, número, fecha y, al modificar, el total |
| Envío al LROE de Bizkaia, modelos 240 y 140: alta (`A00`), modificación (`M00`) y anulación (`AN0`); lotes de hasta 1.000, GZIP y cabeceras `eus-bizkaia-n3` | ✅ Reconstruye los cuatro envíos oficiales (alta y anulación de los dos modelos) y lee sus respuestas; probado con TLS mutuo |
| Modelo 140: datos de renta por ingreso (epígrafe del IAE, territorio, referencia catastral, IRPF distinto de la base del IVA, criterio de caja) | ✅ |
| Consulta del LROE (`C00`), modelos 240 y 140: filtros, paginación, estado de cada factura y recuperación del eslabón | ✅ Reconstruye las dos peticiones oficiales y lee sus respuestas con y sin resultados; probado con TLS mutuo |
| Kontsulta (Gipuzkoa y Álava): facturas enviadas por páginas y última factura, con estado, errores y recuperación del eslabón | ✅ Reconstruye las peticiones oficiales de los dos territorios y valida con sus XSD (Álava 1.2.3, Gipuzkoa 1.0); probado con TLS mutuo |
| Modelo 240: otra información con trascendencia tributaria (representante, inmuebles, acuerdo de facturación, referencia externa, cupón, entidad sucedida, disposiciones adicionales 2.ª y 5.ª, hidrocarburos) | ✅ Cumple el XSD; aplica las reglas B4_2000300, B4_2000301 y B4_2000302, las dos últimas contra el propio fichero |

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

### Corrección con Zuzendu (Gipuzkoa y Álava)

El fichero original no se toca ni se vuelve a firmar. Zuzendu lleva la factura corregida con la huella y los 100 primeros caracteres de la firma del original, y **no se firma**.

```java
// SUBSANAR: el original fue rechazado (01). MODIFICAR: fue recibido y hay que corregir datos (no el importe total).
ZuzenduFile fix = tbai.correct(correctedInvoice, original.getSignedXml(), ZuzenduAction.SUBSANAR);
SubmissionResult result = client.submitCorrection(fix);

ZuzenduFile annulFix = tbai.correctAnnulment(null, rejectedAnnulment);   // null: reenviar los mismos datos
```

### Envío (Bizkaia, LROE)

En Bizkaia el fichero TicketBAI no se envía al expedir: se registra en el LROE dentro de su plazo (trimestral, o 4 días en un régimen tipo SII), en lotes de un solo obligado.

```java
// Persona jurídica (modelo 240)
LroeBatch batch = LroeBatch.of("B12345674", "CONSTRUCCIONES DE PRUEBA SL", Year.of(2026),
        LroeOperation.INVOICES, List.of(issued1.getSignedXml(), issued2.getSignedXml()));   // hasta 1.000

// Persona física (modelo 140): cada ingreso con sus datos de renta
LroeBatch autonomo = LroeBatch.builder(LroeModel.MODEL_140,
                LroeTaxpayer.individual("79732487C", "ANE", "ARRIOLA", "LEJARDI"), Year.of(2026), LroeOperation.INVOICES)
        .add(issued.getSignedXml(), IncomeDetail.activity("197330"))
        .build();

// Modelo 240 con otra información con trascendencia tributaria
LroeBatch conInmueble = LroeBatch.builder(LroeModel.MODEL_240,
                LroeTaxpayer.legalEntity("B12345674", "CONSTRUCCIONES DE PRUEBA SL"), Year.of(2026), LroeOperation.INVOICES)
        .add(issued.getSignedXml(), OtherTaxInformation.create()
                .property(OtherTaxInformation.PropertySituation.BASQUE_COUNTRY_OR_NAVARRE, "U1234567890")
                .externalReference("PEDIDO-4711"))
        .build();

LroeResult result = new LroeClient(Environment.TEST, tls).submit(batch);
// result.getStatus(): CORRECT / PARTIALLY_CORRECT / INCORRECT; result.getRecords(): estado de cada factura
```

La modificación (`LroeOperation.MODIFICATIONS`) usa el mismo esquema que el alta: se envía el mismo fichero TicketBAI firmado con los datos que lo acompañan corregidos (en el 140, los de renta).

La consulta (`C00`) devuelve, por páginas, las facturas registradas con su estado. Sirve para conciliar y para recuperar el último eslabón de la cadena si se pierde:

```java
LroeQueryResult page = new LroeClient(Environment.TEST, tls).query(
        LroeQuery.of(LroeModel.MODEL_240, taxpayer, Year.of(2026)).series("B2026").page(1));
page.getInvoices();        // serie, número, fecha, hora, total, estado (Correcto / AceptadoConErrores / Anulado)
page.hasMoreRecords();     // hay más páginas
page.latest().map(LroeQueryResult.Invoice::toChainLink);   // candidata a último eslabón de esta página
```

El nombre del obligado en el XML se toma de `LroeTaxpayer` («APELLIDO1 APELLIDO2 NOMBRE» en una persona física). Si la Diputación lo tiene en otro orden, `withFullName(...)` lo fija; los ejemplos oficiales de alta y de consulta usan órdenes distintos.

### Consulta (Gipuzkoa y Álava, Kontsulta)

El XML de consulta no se firma. El certificado decide qué se ve: el del emisor, todas sus facturas; un representante o un dispositivo, solo las que envió con ese certificado. Los envíos rechazados no aparecen.

```java
KontsultaClient kontsulta = new KontsultaClient(Territory.ARABA, Environment.TEST, tls);
KontsultaResult page = kontsulta.query(KontsultaQuery.of(Territory.ARABA, "B12345674", Year.of(2026), KontsultaQuery.Period.Q1)
        .series("A2026").showErrors(true).page(1));
KontsultaResult last = kontsulta.latest("B12345674");   // la última factura admitida
last.latest().map(KontsultaResult.Invoice::toChainLink);
```

> [!WARNING]
> Álava actualiza los datos de consulta una vez al día (devuelve los del día anterior): no sirve para recuperar la cadena justo después de emitir. Gipuzkoa no ha publicado la URL de producción de Kontsulta; hasta entonces, se pasa con el constructor de URIs explícitas. Al recorrer varias páginas, fije `submittedBetween(...)`: sin él, una factura recibida entre dos peticiones puede repetirse en dos páginas.

> [!IMPORTANT]
> El encadenamiento es responsabilidad de quien llama: hay que guardar `getChainLink()` y pasarlo a la siguiente factura del mismo emisor, en orden y sin emitir dos facturas del mismo emisor a la vez.

## Pendiente de confirmar con las Diputaciones

- **URL del QR:** el código TicketBAI lleva caracteres del Base64 de la firma (`+`, `/`). El SDK los codifica (`%2B`, `%2F`) y calcula el CRC-8 sobre la URL ya codificada, como dice el Anexo V. Hay que comprobarlo con la herramienta de pruebas del QR de Bizkaia (`pruebatuz.bizkaia.eus/B4PEQR0C/test`) y con las URL de pruebas de Gipuzkoa y Álava.
- **Esquemas de Zuzendu:** se usan los de Álava (`ZuzenduAlta/ZuzenduAnula_ticketBaiV1-2-2.xsd`) para los dos territorios; falta cotejarlos con los de Gipuzkoa.
- **Política de firma de Bizkaia:** el SDK firma con la v1.2 vigente (OF 601/2025). Los ejemplos oficiales del LROE usan la v1.0; no está confirmado si Bizkaia sigue aceptando ficheros con la v1.0.
- **Kontsulta:** no hay respuestas oficiales con datos (la de Gipuzkoa lleva nombres de campo como valores), así que la lectura se prueba con respuestas escritas a partir de los XSD. No está documentado cuándo se rellena el bloque `Error` (obligatorio en toda respuesta) ni con qué código HTTP responde un error de la consulta. El esquema de respuesta 1.0 de Gipuzkoa importa TicketBAI 1.2.1 y usa un tipo que la 1.2.2 ya no tiene, por lo que no se incluye.
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
