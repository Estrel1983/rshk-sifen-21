# rshk-jsifenlib (W3 fork)

Fork of the Roshka SIFEN library (Paraguay electronic documents).

- **`0.2.4b.4`**: the version in production. It runs on **Java 8** and includes the W3 fix for
  SIFEN error 2359 (Gravado Parcial subtotal, `TgTotSub`). The first commit of this repository is
  exactly the production sources; compiled, they match the production jar class by class.
- **`0.2.5` and later**: built for **Java 21 only** (`maven.compiler.release=21`). This version
  does not run on Java 8. Do not deliver it to any system that still runs on Java 8.

Build: `mvn package` (JDK 21).
