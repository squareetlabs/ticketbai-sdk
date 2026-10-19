# TicketBAI Java - Facturación electrónica en el País Vasco

Paquete Java 11+ para emitir y registrar facturas con **TicketBAI**, el sistema de las Haciendas Forales de Álava (Araba), Bizkaia (Batuz) y Gipuzkoa. Es el equivalente de [`com.squareetlabs:verifactu`](https://github.com/squareetlabs/verifactu-sdk) para los territorios forales, y puede convivir con él en la misma aplicación.

## Características principales

- **Modelo de factura** con constructor (`Invoice.builder()`): emisor, destinatarios, líneas, claves de régimen, desglose por factura o por tipo de operación, rectificativas y facturas simplificadas
- **Enums** para los códigos TicketBAI (clave de régimen, causa de exención, tipo de no exenta, causa de no sujeción, rectificativa, tipo de documento)
- **Firma digital XAdES-EPES** enveloped con la política de firma de cada territorio (EU DSS)
- **Código TicketBAI** de 39 caracteres (con su CRC-8) y **URL del QR** por territorio y entorno
- **Encadenamiento** con la factura anterior del mismo emisor (serie, número, fecha y los 100 primeros caracteres de su firma)
- **Validación local** contra los XSD oficiales y con las reglas de negocio publicadas, antes de firmar
- **Envío REST a Gipuzkoa y Álava** (alta y anulación) con TLS mutuo
- **Zuzendu** (Gipuzkoa y Álava): subsanar o modificar un alta y subsanar una anulación, sin volver a firmar el original
- **LROE de Bizkaia**: modelos 240 (personas jurídicas) y 140 (personas físicas), alta, modificación, anulación y consulta
- **Consultas Kontsulta** (Gipuzkoa y Álava): facturas enviadas y última factura, para conciliar y recuperar la cadena
- **XML con DOM, sin JAXB**: no añade dependencias de JAXB y convive con el SDK de VERI\*FACTU

## Instalación

### Opción 1: Desde Maven Central

Todavía no está publicado. Se publicará como:

```xml
<dependency>
    <groupId>com.squareetlabs</groupId>
    <artifactId>ticketbai</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Opción 2: Desde el repositorio local

```bash
git clone https://github.com/squareetlabs/ticketbai-sdk.git
cd ticketbai-sdk
./mvnw clean install
```

y en tu proyecto, la versión `1.0.0-SNAPSHOT`.

## Configuración

Una instancia de `TicketBai` emite las facturas de **un territorio y un entorno**. Necesita los datos del software garante (la licencia TicketBAI que da la Diputación al desarrollador) y el certificado con el que se firma:

```java
Software software = new Software(
        "TBAIGIPRE00000000001",                    // licencia TicketBAI del software
        Party.withNif("A99800005", null),          // entidad desarrolladora
        "MI SOFTWARE DE FACTURACION", "1.0");      // nombre y versión

try (TicketBaiSigner signer = TicketBaiSigner.pkcs12(p12Bytes, password)) {   // password: char[]
    TicketBai tbai = TicketBai.builder()
            .territory(Territory.GIPUZKOA)         // ARABA, BIZKAIA o GIPUZKOA
            .environment(Environment.TEST)         // TEST o PRODUCTION
            .software(software)
            .signer(signer)
            .deviceSerial("SN-0001")               // opcional: número de serie del dispositivo
            .build();
}
```

El firmante también puede ser una tarjeta o un HSM (`TicketBaiSigner.fromToken(...)`, cualquier `SignatureTokenConnection` de EU DSS).

### Conexión con las Diputaciones

Los envíos y las consultas usan TLS mutuo con el certificado del obligado, de su representante, un sello o un dispositivo:

```java
TlsConfig tls = TlsConfig.builder()
        .clientCertificate(p12Bytes, password)
        // las raíces Sectigo R46/E46 (cambio de 2026-2027) ya vienen incluidas; .trust(...) para añadir otras
        .build();
```

#### Servicios según territorio y entorno

| Servicio | Territorio | Pruebas | Producción |
|---|---|---|---|
| Alta, anulación y Zuzendu | Gipuzkoa | `https://tbai-z.prep.gipuzkoa.eus/sarrerak/` | `https://tbai-z.egoitza.gipuzkoa.eus/sarrerak/` |
| Alta, anulación y Zuzendu | Álava | `https://pruebas-ticketbai.araba.eus/TicketBAI/v1/` | `https://ticketbai.araba.eus/TicketBAI/v1/` |
| LROE (alta, modificación, anulación) | Bizkaia | `https://pruesarrerak.bizkaia.eus/N3B4000M/aurkezpena` | `https://sarrerak.bizkaia.eus/N3B4000M/aurkezpena` |
| LROE (consulta) | Bizkaia | `https://pruesarrerak.bizkaia.eus/N3B4001M/kontsulta` | `https://sarrerak.bizkaia.eus/N3B4001M/kontsulta` |
| Kontsulta | Gipuzkoa | `https://tbai-k.prep.gipuzkoa.eus/kontsulta/` | No publicada: se indica con el constructor de URIs explícitas |
| Kontsulta | Álava | `https://pruebas-ticketbai.araba.eus/TicketBAIConsultas/v1/` | `https://ticketbai.araba.eus/TicketBAIConsultas/v1/` |

Todos los clientes tienen además un constructor con las URIs y los tiempos de espera explícitos.

## Alcance y limitaciones

El SDK cubre la emisión, el envío y la corrección de facturas en los tres territorios, y las consultas. Todavía no cubre:
- Las **pruebas contra los entornos reales**: todo está probado con los ejemplos oficiales y con un servidor local con TLS mutuo. Faltan un certificado de pruebas y la licencia de pruebas de Álava.
- Los **otros capítulos del LROE** de Bizkaia (gastos, bienes de inversión, operaciones intracomunitarias, etc.) y el subcapítulo 1.2 (facturas sin software garante).
- La **imagen del QR**: el SDK da la URL; la imagen la genera quien llama.
- El servicio **Osatu** de Gipuzkoa.

Pendiente de confirmar con las Diputaciones:
- **URL del QR:** el código TicketBAI lleva caracteres del Base64 de la firma (`+`, `/`). El SDK los codifica (`%2B`, `%2F`) y calcula el CRC-8 sobre la URL ya codificada, como dice el Anexo V. Hay que comprobarlo con la herramienta de pruebas del QR de Bizkaia (`pruebatuz.bizkaia.eus/B4PEQR0C/test`) y con las URL de pruebas de Gipuzkoa y Álava.
- **Esquemas de Zuzendu:** se usan los de Álava (`ZuzenduAlta/ZuzenduAnula_ticketBaiV1-2-2.xsd`) para los dos territorios; falta cotejarlos con los de Gipuzkoa.
- **Política de firma de Bizkaia:** el SDK firma con la v1.2 vigente (OF 601/2025). Los ejemplos oficiales del LROE usan la v1.0; no está confirmado si Bizkaia sigue aceptando ficheros con la v1.0.
- **Kontsulta:** no hay respuestas oficiales con datos, así que la lectura se prueba con respuestas escritas a partir de los XSD. No está documentado cuándo se rellena el bloque `Error` ni con qué código HTTP responde un error de la consulta.
- **Tolerancia de los cuadres:** no está publicada; el validador es exacto por defecto.

## Novedades

**1.0.0** (en desarrollo, sin publicar) — Primera versión, sobre `javax` y DSS 5.11.1 (línea 1.x, para convivir con `com.squareetlabs:verifactu` 1.x; la línea Jakarta 2.x está en `main`). Estado de cada pieza y cómo se ha comprobado:

| Pieza | Comprobación |
|---|---|
| CRC-8, código TicketBAI y URL del QR | Vectores oficiales del Anexo V y ejemplo oficial de Gipuzkoa |
| XML de alta y anulación (TicketBAI 1.2.2) | Reproduce la primera factura oficial de Gipuzkoa, la rectificativa de Álava y su anulación |
| Firma XAdES-EPES por territorio | Verifica las firmas reales de los ejemplos de Álava y Bizkaia |
| Validación de negocio | Reglas publicadas, cada una con su fuente |
| Envío REST a Gipuzkoa y Álava | Servidor local con TLS mutuo y respuestas oficiales de Álava |
| Zuzendu | Huella y firma del original; impide cambiar emisor, serie, número, fecha y, al modificar, el total |
| LROE 240 y 140: alta, modificación y anulación | Reconstruye los cuatro envíos oficiales y lee sus respuestas |
| Modelo 140: datos de renta | Esquema oficial |
| Modelo 240: otra información con trascendencia tributaria | Esquema oficial y reglas B4_2000300, B4_2000301 y B4_2000302 |
| Consulta del LROE (`C00`) | Reconstruye las dos peticiones oficiales y lee sus respuestas con y sin resultados |
| Kontsulta (Gipuzkoa y Álava) | Reconstruye las peticiones oficiales y valida con sus XSD (Álava 1.2.3, Gipuzkoa 1.0) |

## Detalle de la respuesta

**Gipuzkoa y Álava.** `TicketBaiRestClient` devuelve un `SubmissionResult`:

| Método | Contenido |
|---|---|
| `getStatus()` / `isReceived()` | `RECEIVED` (código `00`, puede traer avisos) o `REJECTED` (`01`) |
| `getStatusCode()` | Código de estado tal como llega |
| `getIdentifier()` | Identificador TicketBAI de la factura |
| `getReceivedAt()` | Fecha de recepción |
| `getDescription()` | Descripción del estado |
| `getCsv()` | CSV de la recepción |
| `getValidationResults()` | Códigos de validación con su descripción en castellano y en euskera |
| `getRawResponse()` | Respuesta completa: guárdala junto al fichero |

**Bizkaia.** `LroeClient.submit` devuelve un `LroeResult`: el estado del envío (`CORRECT`, `PARTIALLY_CORRECT`, `INCORRECT`), su identificativo y, por cada fichero, un registro con serie, número, fecha, estado (`CORRECT`, `ACCEPTED_WITH_ERRORS`, `INCORRECT`, `ANNULLED`) y el código y la descripción del error.

Si no hay respuesta válida (sin conexión, HTTP de error, respuesta ilegible), los clientes lanzan `TransportException` con el código HTTP, si lo hay.

## Uso rápido: crear y enviar una factura

```java
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
issued.getWarnings();                                       // avisos de la validación (no impiden emitir)

SubmissionResult result = new TicketBaiRestClient(Territory.GIPUZKOA, Environment.TEST, tls)
        .submitInvoice(issued.getSignedXml());
```

`issue` valida la factura, la firma, comprueba el XSD y calcula el código y el QR. Con errores de validación lanza `InvalidInvoiceException` y no firma nada.

> [!IMPORTANT]
> El encadenamiento es responsabilidad de quien llama: guarda `getChainLink()` y pásalo a la siguiente factura del mismo emisor, en orden y sin emitir dos facturas del mismo emisor a la vez.

### Bizkaia: la factura no se envía al expedir

En Bizkaia el fichero firmado se registra después en el LROE, dentro de su plazo (trimestral, o 4 días en un régimen tipo SII), en lotes de un solo obligado. Ver [LROE de Bizkaia](#lroe-de-bizkaia-modelos-240-y-140).

## Tipos de factura soportados

| Tipo | Cómo se indica |
|---|---|
| Completa | Por defecto |
| Simplificada | `simplified(true)` |
| Completa en sustitución de una simplificada | `substitutesSimplified(true)` |
| Rectificativa R1 a R5 | `rectification(...)`, ver abajo |

| Código | Causa |
|---|---|
| `R1` | Error fundado en derecho y art. 80 Uno, Dos y Seis de la normativa del IVA |
| `R2` | Art. 80 Tres (concurso de acreedores) |
| `R3` | Art. 80 Cuatro (créditos incobrables) |
| `R4` | Resto de causas |
| `R5` | Rectificativa de facturas simplificadas |

### Facturas rectificativas (R1-R5)

Por diferencias, los importes de la factura son los de la diferencia e indica qué facturas corrige. Por sustitución, lleva además la base y la cuota de la factura original:

```java
.rectification(Rectification.byDifferences(RectificationCode.R1,
        List.of(new InvoiceReference("F2026", "1", LocalDate.of(2026, 1, 15)))))

.rectification(Rectification.bySubstitution(RectificationCode.R4, baseOriginal, cuotaOriginal,
        null, List.of(new InvoiceReference("F2026", "1", LocalDate.of(2026, 1, 15)))))   // null: sin recargo de equivalencia
```

### Destinatarios

Un destinatario con NIF español se crea con `Party.withNif(nif, nombre)`; uno **extranjero**, con el bloque `IDOtro`: país (ISO 3166-1 alfa-2), tipo de documento e identificador:

```java
Party.withOtherId("FR", OtherIdType.VAT_NUMBER, "FR12345678901", "CLIENT SARL");
Party.withOtherId("GB", OtherIdType.PASSPORT, "P1234567", "JOHN SMITH");
```

| Enum | Código | Documento |
|---|---|---|
| `VAT_NUMBER` | `02` | NIF-IVA |
| `PASSPORT` | `03` | Pasaporte |
| `OFFICIAL_ID` | `04` | Documento oficial del país de residencia |
| `RESIDENCE_CERTIFICATE` | `05` | Certificado de residencia |
| `OTHER_DOCUMENT` | `06` | Otro documento probatorio |

`withAddress(codigoPostal, direccion)` añade la dirección, que Gipuzkoa pide en la factura completa.

### Reintentos: reenviar el mismo fichero

Si un envío falla (`TransportException`), no sabes si la Diputación llegó a recibirlo. Reintenta con el **mismo** fichero firmado: nunca lo regeneres ni lo vuelvas a firmar, porque la siguiente factura ya está encadenada con esa firma. Para recuperar el código, el QR o el eslabón de un fichero guardado: `SignedInvoiceReader.read(bytes)`.

### Corrección tras un rechazo: Zuzendu (Gipuzkoa y Álava)

El fichero original no se toca ni se vuelve a firmar. Zuzendu lleva la factura corregida con la huella y los 100 primeros caracteres de la firma del original, y **no se firma**:

```java
// SUBSANAR: el original fue rechazado (01). MODIFICAR: fue recibido y hay que corregir datos (no el importe total).
ZuzenduFile fix = tbai.correct(correctedInvoice, original.getSignedXml(), ZuzenduAction.SUBSANAR);
SubmissionResult result = client.submitCorrection(fix);

ZuzenduFile annulFix = tbai.correctAnnulment(null, rejectedAnnulment);   // null: reenviar los mismos datos
```

En Bizkaia la corrección es una modificación en el LROE (`LroeOperation.MODIFICATIONS`).

## Anulación de una factura

Una **anulación** deja sin efecto una factura ya emitida. No la borra: es un fichero firmado aparte (`AnulaTicketBai`), que no entra en la cadena.

> [!WARNING]
> Si lo que necesitas es **corregir importes** de una factura emitida, no la anules: emite una **rectificativa** (R1-R5).

```java
SignedXml annulment = tbai.annul(Annulment.of(invoice));
// o new Annulment(nifEmisor, nombreEmisor, serie, numero, fecha)

client.submitAnnulment(annulment);                              // Gipuzkoa y Álava
LroeBatch.of(nif, nombre, Year.of(2026), LroeOperation.ANNULMENTS, List.of(annulment));   // Bizkaia
```

## LROE de Bizkaia (modelos 240 y 140)

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
```

| Operación | `LroeOperation` | Ficheros |
|---|---|---|
| Alta (`A00`) | `INVOICES` | `TicketBai` firmados |
| Modificación (`M00`) | `MODIFICATIONS` | El mismo `TicketBai`, con los datos que lo acompañan corregidos |
| Anulación (`AN0`) | `ANNULMENTS` | `AnulaTicketBai` firmados |

El envío va comprimido con GZIP y con las cabeceras `eus-bizkaia-n3-*`. El nombre del obligado se toma de `LroeTaxpayer` («APELLIDO1 APELLIDO2 NOMBRE» en una persona física); `withFullName(...)` lo fija si la Diputación lo tiene en otro orden.

El bloque de otra información con trascendencia tributaria del 240 aplica las reglas de Batuz: la referencia catastral es obligatoria salvo en las situaciones 3 y 4 (`B4_2000300`), el cupón solo vale en una rectificativa R1 o R5 (`B4_2000301`) y la facturación de las disposiciones adicionales 2.ª y 5.ª no vale si emite un tercero o el destinatario (`B4_2000302`).

## Consultas

Sirven para conciliar el estado de las facturas y para recuperar el último eslabón de la cadena si se pierde.

**Bizkaia (LROE, `C00`):**

```java
LroeQueryResult page = new LroeClient(Environment.TEST, tls).query(
        LroeQuery.of(LroeModel.MODEL_240, taxpayer, Year.of(2026)).series("B2026").page(1));
page.getInvoices();        // serie, número, fecha, hora, total, estado (Correcto / AceptadoConErrores / Anulado)
page.hasMoreRecords();     // hay más páginas
page.latest().map(LroeQueryResult.Invoice::toChainLink);   // candidata a último eslabón de esta página
```

**Gipuzkoa y Álava (Kontsulta):** el XML de consulta no se firma.

```java
KontsultaClient kontsulta = new KontsultaClient(Territory.ARABA, Environment.TEST, tls);
KontsultaResult page = kontsulta.query(KontsultaQuery.of(Territory.ARABA, "B12345674", Year.of(2026), KontsultaQuery.Period.Q1)
        .series("A2026").showErrors(true).page(1));
KontsultaResult last = kontsulta.latest("B12345674");   // la última factura admitida
last.latest().map(KontsultaResult.Invoice::toChainLink);
```

> [!WARNING]
> Álava actualiza los datos de consulta una vez al día (devuelve los del día anterior): no sirve para recuperar la cadena justo después de emitir. Al recorrer varias páginas, fija `submittedBetween(...)`: sin él, una factura recibida entre dos peticiones puede repetirse en dos páginas.

## Representación de terceros

- **Quién envía:** el certificado de `TlsConfig` puede ser del obligado, de un representante, un sello o un dispositivo. En Kontsulta decide qué se ve: el del emisor, todas sus facturas; un representante o un dispositivo, solo las que envió con ese certificado.
- **Quién emite la factura:** `issuedBy(IssuedBy.THIRD_PARTY)` o `issuedBy(IssuedBy.RECIPIENT)` (`EmitidaPorTercerosODestinatario`).
- **Representante del destinatario** (LROE 240): `OtherTaxInformation.representativeNif(...)`.

> [!CAUTION]
> Actuar en nombre de otro obligado exige su autorización ante la Diputación. Consulta con tu asesoría fiscal antes de activarlo en producción.

## Validación local

`TicketBai.issue` y `correct` validan antes de firmar. El validador también se puede usar suelto:

```java
List<ValidationIssue> issues = InvoiceValidator.strict().validate(invoice, Territory.GIPUZKOA);
issues.stream().filter(ValidationIssue::isError).forEach(System.out::println);   // campo, mensaje y fuente de la regla

TicketBai.builder().validator(InvoiceValidator.strict().withTolerance(new BigDecimal("0.01")));   // margen en los cuadres
```

Cada `ValidationIssue` es un `ERROR` (la Diputación rechazaría el fichero: no se firma) o un `WARNING` (lo aceptaría con aviso), con el campo, el mensaje y la fuente de la regla. Entre otras, comprueba:
- **Errores:** importe total = suma de las líneas y del desglose; claves de régimen repetidas o incompatibles (01 con 02); clave 14 con destinatario Administración Pública (NIF P, Q, S o V); reglas de `IDOtro` (país obligatorio salvo con NIF-IVA, con país ES solo pasaporte); exención E5 con NIF-IVA; desglose por tipo de operación con destinatario extranjero; en Gipuzkoa, líneas de detalle y destinatario en la factura completa.
- **Avisos:** en Gipuzkoa, código postal y dirección del destinatario en la factura completa.

Además, cada fichero firmado se valida contra el XSD oficial (`XsdValidator`, con los esquemas incluidos en el paquete, sin acceso a la red).

## Generación del código TicketBAI y del QR

`issue` devuelve los dos. Para un fichero ya firmado:

```java
SignedInvoice signed = SignedInvoiceReader.read(bytes);
TbaiIdentifier code = signed.toTbaiIdentifier();       // TBAI-NIF-DDMMAA-13 caracteres de la firma-CRC
String qr = QrUrlBuilder.build(Territory.ARABA, Environment.PRODUCTION, code, serie, numero, importeTotal);
```

| Territorio | Pruebas | Producción |
|---|---|---|
| Álava | `https://pruebas-ticketbai.araba.eus/tbai/qrtbai/` | `https://ticketbai.araba.eus/tbai/qrtbai/` |
| Bizkaia | `https://batuz.eus/QRTBAI/` | `https://batuz.eus/QRTBAI/` |
| Gipuzkoa | `https://tbai.prep.gipuzkoa.eus/qr/` | `https://tbai.egoitza.gipuzkoa.eus/qr/` |

La URL lleva el código, la serie, el número y el importe codificados, y el CRC-8 al final (Anexo V). La imagen del QR la genera quien llama.

## Firma digital XAdES

`TicketBai` firma cada fichero de alta y de anulación con **XAdES-EPES enveloped**, con la política de firma del territorio, RSA con SHA-256, transformación `enveloped-signature` y C14N inclusiva (como los ficheros oficiales). Los ficheros Zuzendu y las consultas no se firman.

Para firmar o verificar por tu cuenta:

```java
SignedXml signed = signer.sign(unsignedXml, Territory.BIZKAIA);
List<String> problems = SignatureVerifier.verify(signed.getBytes(), Territory.BIZKAIA);   // vacía si es válida
```

## Testing

```bash
./mvnw test
```

Los tests usan los ejemplos oficiales de las tres Diputaciones (ver [`src/test/resources/examples/`](src/test/resources/examples/)) y un servidor local con TLS mutuo. No se conectan a las Diputaciones.

## Estructura del proyecto

```
src/
├── main/java/com/squareetlabs/ticketbai/
│   ├── TicketBai, Territory, Environment   # Fachada: emitir, anular y corregir
│   ├── model/        # Factura, destinatarios, desglose, rectificativas, anulación y enums de códigos
│   ├── xml/          # Construcción del XML (alta, anulación y Zuzendu) con DOM
│   ├── signature/    # Firma XAdES-EPES (EU DSS) y verificación (API del JDK)
│   ├── identifier/   # Código TicketBAI, CRC-8 y URL del QR
│   ├── chain/        # Encadenamiento y lectura de ficheros firmados
│   ├── validation/   # Validación de negocio y contra los XSD
│   └── transport/    # TLS mutuo; rest/ (Gipuzkoa y Álava), lroe/ (Bizkaia) y kontsulta/
├── main/resources/
│   ├── xsd/          # Esquemas oficiales (TicketBAI 1.2.2, Zuzendu, LROE y Kontsulta)
│   └── trust/        # Raíces Sectigo R46 y E46
└── test/             # Tests con los ejemplos oficiales
```

## Dependencias principales

- **EU DSS 5.11.1**: firma XAdES (la misma versión que el SDK de VERI\*FACTU 1.x)
- **JDK**: `java.net.http` para el transporte, DOM para el XML y `javax.xml.crypto` para verificar firmas
- **JUnit 5**: testing

## Requisitos

- Java 11 o superior
- Maven 3+ (o el `./mvnw` incluido)
- Certificado digital válido para firma electrónica
- Licencia TicketBAI del software garante, que da cada Diputación

## Documentación oficial

- **Gipuzkoa**: [documentación y normativa TicketBAI](https://www.gipuzkoa.eus/es/web/ogasuna/ticketbai/documentacion-y-normativa)
- **Álava**: [documentación técnica](https://web.araba.eus/es/hacienda/ticketbai/documentacion-tecnica)
- **Bizkaia**: [documentación técnica de Batuz](https://www.batuz.eus/es/documentacion-tecnica)

El origen y las particularidades de cada ejemplo oficial usado en los tests están en [`src/test/resources/examples/README.md`](src/test/resources/examples/README.md).

## Contribuir

Las contribuciones son bienvenidas. Por favor:

1. Haz un fork del proyecto
2. Crea una rama para tu feature (`git checkout -b feature/nueva-funcionalidad`)
3. Haz commit de tus cambios (`git commit -m 'feat: añadir nueva funcionalidad'`)
4. Haz push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Abre un Pull Request

No copies código de implementaciones con licencia GPL: este paquete es MIT.

## Licencia

Este paquete es open-source bajo la [Licencia MIT](LICENSE).

## Soporte

- **Documentación técnica**: ver [Documentación oficial](#documentación-oficial)
- **Issues**: [github.com/squareetlabs/ticketbai-sdk/issues](https://github.com/squareetlabs/ticketbai-sdk/issues)

## Autores

- **Alberto Rial Barreiro** - [SquareetLabs](https://www.squareet.com)
- **Jacobo Cantorna Cigarrán** - [SquareetLabs](https://www.squareet.com)

---

Si este paquete te ha sido útil, ¡no olvides darle una estrella en GitHub! ⭐
