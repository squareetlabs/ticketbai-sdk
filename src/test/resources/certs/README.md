# Certificados de prueba

`test-signer.p12`: certificado RSA 2048 autofirmado, generado con `keytool` solo para los tests del SDK. Contraseña: `ticketbai-test`. **No es válido ante ninguna Diputación.**

`test-server.p12` / `test-server.cer`: certificado de servidor autofirmado para `localhost` (y `127.0.0.1`), para el servidor HTTPS con TLS mutuo de los tests de envío. Contraseña: `ticketbai-test`.

`test-signer.cer`: la parte pública de `test-signer.p12`, que el servidor de pruebas usa para aceptar al cliente.
