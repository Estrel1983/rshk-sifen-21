# rshk-jsifenlib (W3 fork)

Fork of the Roshka SIFEN library (Paraguay electronic documents).

- **`0.2.4b.4`**: the version in production. It runs on **Java 8** and includes the W3 fix for
  SIFEN error 2359 (Gravado Parcial subtotal, `TgTotSub`). The first commit of this repository is
  exactly the production sources; compiled, they match the production jar class by class.
- **`0.2.5` and later**: built for **Java 21 only** (`maven.compiler.release=21`). This version
  does not run on Java 8. Do not deliver it to any system that still runs on Java 8.

Build: `mvn package` (JDK 21).

## Running on Java 21

- `javax.xml.soap` (SAAJ) is no longer in the JDK. It comes from `jakarta.xml.soap-api` 1.4.2 and
  `saaj-impl` 1.5.3 (see `pom.xml`); both keep the `javax.xml.soap` package names. At runtime the
  classpath needs `jakarta.xml.soap-api`, `saaj-impl`, `stax-ex` and `jakarta.activation`
  (`mvn dependency:build-classpath -Dmdep.includeScope=runtime`). Do not add an older SAAJ API jar
  (for example `saaj-api-1.3`) next to them: it has the same package names.
- `SignatureHelper` uses only the standard certificate API (no `sun.security.x509`).

## Requirement: the XML sent to SIFEN does not change

The XML this library sends to SIFEN must stay identical, character for character, to the
production version `0.2.4b.4` on Java 8. `tools/sifen-xml-check/check.sh` checks that:

```sh
JDK8_HOME=/path/to/jdk8 JDK21_HOME=/path/to/jdk21 tools/sifen-xml-check/check.sh
```

It builds the root commit of this repository (the production sources) with JDK 8 and the working
tree with JDK 21. Both then build the same requests from `tools/sifen-xml-check/samples/`
(synthetic documents: invoices with IVA 10, IVA 5, exempt and Gravado Parcial items, an invoice
in USD, a credit note), a lot of all of them and two events. Everything is signed with the same
throwaway test certificate, and the files are compared byte by byte:

- what `SoapHelper` writes to the connection, `getRequestSent()` and `generarXml()`;
- the lot (the zipped XML, with the zip timestamp zeroed);
- the result of signature validation.

The script exits with 1 on any difference. Run it after every change to the code or to the
dependencies.
