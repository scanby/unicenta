# Plan de mejora de código — uniCenta oPOS 3.81

> Análisis realizado sobre los 552 archivos Java (~97.460 líneas) de `src-beans`, `src-data` y `src-pos`.
> Estado: **Fase 1 implementada** (ver §4). El resto queda planificado con criterios de aceptación.

---

## 1. Principios rectores

1. **No reescribir de golpe.** Es un POS en producción con 15 años de historia, GUI Builder, scripts BeanShell
   en la BD y 5 motores de base de datos. Una refactorización masiva sin pruebas es más peligrosa que la deuda.
2. **Cambios pequeños, reversibles y con pruebas.** Cada mejora se entrega con tests JUnit y se puede revertir
   con un `git checkout` de un solo archivo.
3. **Compatibilidad hacia atrás.** Los hashes `sha1:`, `plain:`, `empty:` y las contraseñas `crypt:` ya guardadas
   deben seguir funcionando; se migran de forma transparenta cuando el usuario inicia sesión.
4. **Observabilidad antes que silencio.** Ningún `catch {}` sin logging: un fallo silencioso en un POS es un
   cobro perdido sin diagnóstico.
5. **Seguridad por capas.** No basta con cambiar el hash: hay que bloquear RMI, scripts y carga dinámica de clases.

---

## 2. Hallazgos priorizados

| ID | Hallazgo | Riesgo | Esfuerzo | Fase |
|---|---|---|---|---|
| S1 | `Hashcypher` usa SHA-1 sin sal y admite `plain:` | Crítico | M | 1 |
| S2 | `AltEncrypter` = 3DES-ECB con clave derivada del usuario (`cypherkey+user`) | Alto | M | 2 |
| S3 | RMI `InstanceManager` sin autenticación en puerto 1099 | Alto | S | 2 |
| S4 | BeanShell evalúa menús/reportes/events desde la BD → RCE | Crítico | L | 3 |
| S5 | Escapado SQL no estándar (`\'` en vez de `''`) en `DataWriteUtils` | Alto | S | 1 |
| S6 | Carga de driver desde `db.driverlib` configurable | Medio | M | 2 |
| B1 | `warrantyCheck()` solo inspecciona la primera línea del ticket | Medio | S | 1 |
| B2 | `NormalBuilder.getSentence()` ignora un `?` en la posición 0 | Bajo | S | 1 |
| B3 | `catch {}` y `printStackTrace()` sin logging en datos/UI | Medio | S | 1 |
| Q1 | `JPanelTicket` (2.413 líneas) y otros God classes | Alto | L | 4 |
| Q2 | Duplicados (`TicketLineInfo1`, `PaymentInfoCash_original`, reportes `_1`) | Medio | M | 4 |
| T1 | Sin pruebas unitarias ni build de test | Crítico | M | 1 |
| T2 | `build.xml` roto (importa `nbproject/` inexistente) | Alto | S | 1 |
| T3 | Sin JDK/Ant instalado en el entorno de desarrollo actual | Alto | S | 0 |

---

## 3. Roadmap

### Fase 0 — Entorno y línea base ✅ COMPLETADA
- Instalado **Eclipse Temurin JDK 8.0.512.9** (`C:\Program Files\Eclipse Adoptium\jdk-8.0.512.9-hotspot`).
- Instalado **Apache Ant 1.10.14** en `%LOCALAPPDATA%\Temp\opencode\apache-ant-1.10.14`.
- `ant -f build.xml clean test` → **BUILD SUCCESSFUL, 16/16 tests**.
- `ant -f build.xml dist` → **BUILD SUCCESSFUL**, genera `unicentaopos_3.81_bin.zip` y `_src.zip`.
- Durante la verificación se corrigió un fallo preexistente del build: `src-beans` importaba clases de
  `src-pos`, por lo que la compilación por módulos fallaba en un checkout limpio. Ahora los tres módulos
  se compilan juntos; los antiguos targets `compilebeans`/`compiledata`/`compilepos` quedan como alias.
- También se hicieron opcionales los archivos ausentes `logging.properties`, `.project`, `.classpath`
  y `*.launch`, y se sustituyó `unicentaopos_128.png` por `unicentaopos.png`.

### Fase 1 — Seguridad básica, bugs y pruebas ✅ IMPLEMENTADA
| Cambio | Archivos |
|---|---|
| Hash PBKDF2-HMAC-SHA256 con sal aleatoria (16 B, 120.000 iteraciones), verificación en tiempo constante y compatibilidad con formatos antiguos | nuevo `util/PasswordHasher.java`, `util/Hashcypher.java` |
| Migración transparente del hash legacy al iniciar sesión (usa `DataLogicSystem.execChangePassword`) | `forms/JRootApp.java` |
| Escapado SQL estándar de comillas simples (`''`) | `loader/DataWriteUtils.java` |
| Corrección del bucle de reemplazo de parámetros (`>= 0`) | `loader/NormalBuilder.java` |
| Corrección de `warrantyCheck()` (recorre todas las líneas y respeta el flag) | `sales/JPanelTicket.java` |
| Logging en catches silenciosos de arranque, login, impresión, BD y encrypter | `JRootApp`, `JPanelTicket`, `Session`, `AltEncrypter` |
| Build funcional + target `test` + suite JUnit inicial | `build.xml`, `build_working.xml`, `test/**` |
| `.gitignore` para artefactos generados | nuevo `.gitignore` |

**Criterios de aceptación:** `ant clean compilepos test` sin errores; los usuarios antiguos entran con su
contraseña; una contraseña con `'` busca y guarda correctamente en Derby/MySQL/PostgreSQL/Oracle;
ninguna excepción queda sin registrar.

### Fase 2 — Contención de credenciales y periféricos ✅ IMPLEMENTADA

| Cambio | Archivos |
|---|---|
| AES-128-GCM con nonce aleatorio y formato `v2$<nonce>$<ciphertext+tag>`; clave derivada por PBKDF2 de un secreto local aleatorio **+** la frase del usuario. El 3DES/ECB queda **solo** para descifrar valores antiguos | nuevos `util/SecureKeyStore.java`, `util/SecureEncrypter.java`, `util/AltEncrypter.java` |
| Secreto local en `~/.unicentaopos.key` (32 B aleatorios, permisos 0600 en POSIX). **Respaldar este archivo junto con `unicentaopos.properties`**: sin él no se pueden descifrar los valores `v2` | `SecureKeyStore.java` |
| RMI enlazado solo a `127.0.0.1` mediante `RMIClientSocketFactory`/`RMIServerSocketFactory` propios | nuevo `instance/LoopbackSocketFactory.java`, `instance/InstanceManager.java`, `instance/InstanceQuery.java` |
| Token compartido por usuario (`~/.unicentaopos.instance`) exigido en `AppMessage.restoreWindow(token)`; se rechazan tokens vacíos | nuevos `instance/InstanceToken.java`, `instance/AppMessage.java`, `forms/StartPOS.java`, `forms/JRootFrame.java`, `forms/JRootKiosk.java` |
| Validación de `db.driverlib`: debe existir, ser JAR/ZIP y estar dentro del directorio de la aplicación; escape explícito con `db.driverlib.allowexternal=true` | `forms/AppViewConnection.java` |
| SQL con datos bajado de `INFO` a `FINE` en los dos sentences | `loader/PreparedSentence.java`, `loader/StaticSentence.java` |
| 6 tests nuevos (round-trip, tampering, nonce aleatorio, compatibilidad 3DES, estabilidad del token) | `test/.../SecureEncrypterTest.java` |

**Criterio verificado:** `ant -f build.xml clean test` → **22/22 tests OK**.
El registro RMI ya no escucha en interfaces externas y `restoreWindow` solo responde al token local.
Pendiente de verificación externa con `nmap` desde otra máquina.

**Nota operativa:** al guardar la configuración, los valores `crypt:` antiguos se reescriben automáticamente
en formato `v2$`. Hasta entonces siguen siendo legibles con el descifrador legacy.

### Fase 3 — Scripts y ejecución dinámica ✅ IMPLEMENTADA

| Cambio | Archivos |
|---|---|
| Nueva frontera de confianza: todo script BeanShell de `RESOURCES` se compara con la plantilla versionada en `/com/openbravo/pos/templates/`. Si difiere, se descarta y se ejecuta la copia original; si no hay copia original, se rechaza salvo opt-in explícito `-Dunicenta.scripts.allowdb=true` | nuevo `scripting/ScriptSecurity.java`, `forms/JPrincipalApp.java`, `sales/JPanelTicket.java` |
| `dbPassword` **ya no se entrega a los scripts** (antes se desencriptaba y se pasaba como variable a BeanShell). Se mantiene la variable con valor `<redacted>`; opt-in con `-Dscripting.exposeDbPassword=true` | `sales/JPanelTicket.java` |
| Whitelist de nombres de bean: solo `com.openbravo.*`, `net.sf.jasperreports.*` y recursos `/com/openbravo/**.bs`, con rechazo de `..` y caracteres extraños, antes de `Class.forName()` | nuevo `forms/BeanNameValidator.java`, `forms/JRootApp.java` |
| 10 tests nuevos (integridad de scripts, opt-in, nombres inválidos, traversal) | `test/.../ScriptSecurityTest.java`, `test/.../BeanNameValidatorTest.java` |

**Criterio verificado:** `ant -f build.xml clean test` → **32/32 tests OK**.
Modificar un script en `RESOURCES` ya no permite ejecutar código arbitrario: se detecta la diferencia y se
usa la copia del JAR. Las plantillas Velocity de impresión siguen viniendo de la BD, pero no evalúan Java.

**Nota operativa:** las personalizaciones legítimas de scripts/menu deben versionarse como plantillas en el
JAR o habilitarse de forma explícita con `unicenta.scripts.allowdb=true` (se registra un aviso en el log).

### Fase 4 — Calidad y arquitectura 🔶 EN CURSO

**Implementado en esta entrega:**

| Cambio | Archivos |
|---|---|
| Extracción de la lógica de garantías a un colaborador puro y testeable (`TicketWarranty`) | nuevo `ticket/TicketWarranty.java`, `sales/JPanelTicket.java` |
| Extracción de la carga/compilación de Jasper a `JasperReportLoader`, preparando el trabajo asíncrono | nuevo `reports/JasperReportLoader.java`, `reports/JPanelReport.java` |
| Eliminación de 3 clases duplicadas/muertas sin referencias (`TicketLineInfo1`, `SalesDetailInfo_1`, `TicketsBagRestaurantMap`); `PaymentInfoCash_original` **se conserva** porque sí se usa | `git rm` |
| Pipeline CI con GitHub Actions (JDK 8 + Ant + `ant clean test dist` + artefactos) | nuevo `.github/workflows/ci.yml` |
| 6 tests nuevos (garantías en líneas posteriores, loader de reportes) | `test/.../TicketWarrantyTest.java`, `test/.../JasperReportLoaderTest.java` |

**Pendiente en Fase 4:**
- Extraer `TicketStateMachine`, `TicketPricingService` y `TicketPrintService` completos de `JPanelTicket` (2.400 líneas).
- Dividir `JPanelConfigPeripheral` (1.941 líneas) y `DataLogicSales` (1.648 líneas) por dominio.
- Sustituir tipos raw en `BaseSentence`/`SentenceList` y limpiar código comentado muerto (documentado como `TODO`).
- Pasar la compilación Jasper y la importación CSV a `SwingWorker` (el loader ya está aislado; falta el cambio de hilo).
- Migrar a Maven/Gradle manteniendo Ant como build de contingencia.

**Verificación:** `ant -f build.xml clean test` → **38/38 tests OK**.
**Criterio final (aún no alcanzado):** ninguna clase > 800 líneas; cobertura > 60 % en `util` y `loader`; build reproducible.

### Fase 5 — Datos 🔶 EN CURSO

**Implementado:**
- 28 sentencias `StaticSentence` convertidas a `PreparedSentence` (11 en `DataLogicSystem`, 17 en
  `DataLogicSales`): cambio de clave, caja activa, inserción de tickets, movimientos y CSV pasan ahora
  por `PreparedStatement` con parámetros enlazados. Se conservan las 5 sentencias que usan
  `QBFBuilder` (requieren `ISQLBuilderStatic`) y el `m_dummy` sin parámetros.
- Compilación y 38 tests verificados tras la migración.

**Pendiente en Fase 5:**
- Reescribir `QBFBuilder`/`NormalBuilder` para generar SQL con `?` y alimentar `PreparedSentence`
  (es el último camino de interpolación de literales; afecta a todos los buscadores y reportes).
- Sustituir el SQL concatenado de `RestaurantDBUtils`, `JMooringDetails`, `PromoEditor`,
  `KitchenDisplay`, `JProductAttEdit` y `ProductsEditor.setCurrentStock()`.
- Añadir `CHECK`/`FK`/`NOT NULL` a los 5 esquemas y un pool de conexiones.
- Migración versionada tipo Flyway en lugar de los ~100 scripts `*-upgrade-*.sql` manuales.

**Criterio final (aún no alcanzado):** 0 concatenaciones de valores literales en SQL; migraciones
automatizadas y probadas contra los 5 motores.

---

## 4. Detalle de lo implementado en Fase 1

1. **`util/PasswordHasher.java`** (nuevo): PBKDF2-HMAC-SHA256 implementado con `javax.crypto.Mac`
   (disponible en Java 6+), 120.000 iteraciones, sal de 16 bytes de `SecureRandom`,
   formato `pbkdf2$<iter>$<saltHex>$<hashHex>`, comparación en tiempo constante, verificación legacy.
2. **`util/Hashcypher.java`**: `hashString()` y `authenticate()` delegan en `PasswordHasher`.
   Las contraseñas vacías siguen devolviendo `empty:` por compatibilidad (ver Fase 2).
3. **`forms/JRootApp.java`**: tras un login con contraseña correcta, si el hash es legacy se recalcula
   con PBKDF2 y se persiste con `execChangePassword`; se añade `Logger` y se sustituyen 3 catches silenciosos.
4. **`loader/DataWriteUtils.java`**: `'` → `''` (SQL estándar). Se mantienen `\n`/`\r`/`\\` por compatibilidad
   y se documenta la deuda pendiente (Fase 5).
5. **`loader/NormalBuilder.java`**: `indexOf('?', iLast) > 0` → `>= 0`.
6. **`sales/JPanelTicket.java`**: `warrantyCheck()` recorre todas las líneas sin `return` prematuro;
   se registra el `BasicException` del cálculo de mooring.
7. **`loader/Session.java`**, **`util/AltEncrypter.java`**: logging de errores y fail-fast en el encrypter.
8. **`build.xml` / `build_working.xml`**: `build.xml` pasa a importar el build funcional; versión alineada a 3.81;
   compilación unificada de los 3 módulos (alias antiguos conservados); nuevos targets `clean`, `compile.tests`,
   `test` y `test.run`; recursos opcionales marcados como `failonerror="false"`.
9. **`test/`**: 16 pruebas JUnit 4 sobre hash, escapado SQL y builders.
10. **`.gitignore`**: excluye `build/`, `*.class`, datos de Derby y archivos de IDE.

---

## 5. Verificación (ejecutada correctamente)

```bat
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.512.9-hotspot
set ANT_HOME=C:\Users\CASA\AppData\Local\Temp\opencode\apache-ant-1.10.14
set PATH=%JAVA_HOME%\bin;%ANT_HOME%\bin;%PATH%
ant -f build.xml clean test
ant -f build.xml dist
```

Resultado: **38 tests OK** (16 de Fase 1 + 6 de Fase 2 + 10 de Fase 3 + 6 de Fase 4), JAR y ambos ZIP de distribución generados.
Además, el target `ant installer.windows` genera `unicentaopos_3.81_windows_setup.exe` con **Inno Setup 6**
(instalador Windows sin permisos de administrador); verificado con instalación silenciosa en directorio temporal.
El target `ant release` ejecuta `clean test dist installer.windows` completo.

**Módulo de actualizaciones silenciosas (nuevo):** pestaña "Actualizaciones" en la configuración
general (`JPanelConfigUpdates`) + `UpdateService` (check HTTPS, comparación de versiones, descarga y
silent-install opcional) + hilo daemon en `StartPOS`. Por defecto desactivado, sin ventanas ni mensajes
(solo logging). 5 tests nuevos (43 en total).

**Idioma por defecto (español):** bundles `*_es` instalados en `locales/` y `reports/`, defaults `es_ES`
en `AppConfig` y migración única de configuraciones antiguas desde `StartPOS` (`locale.migration`).
4 tests nuevos (47 en total). Verificado: el config migró de `es_CO` a `es_ES` en el arranque.

**Precios por cantidad (v3.82):** tabla `PRODUCTS_PRICEBREAKS` en los 5 motores + upgrade 3.81→3.82,
pestaña de tramos en `ProductsEditor`, `DataLogicSales.get/setPriceBreaks`, `ProductPricing` aplicado al
añadir líneas y al cambiar cantidades. 6 tests nuevos (53 en total) + DDL validado en Derby.

**Instalación de actualizaciones corregida:** el botón manual ya no depende de `update.autoInstall`;
la instalación se programa al cerrar el POS (`install-pending.cmd`) porque Windows bloquea los JAR
en uso (exit=5 al instalar en caliente). Botón "Instalar descarga pendiente" para reintentar.
2 tests nuevos (55 en total); verificación end-to-end con instalación en directorio temporal.

**Corrección precios por cantidad:** la persistencia se movió de `createValue()` a la transacción del
propio INSERT/UPDATE de PRODUCTS (parámetro extra 25), y el precio base viaja en la línea
(`product.pricebase`) para recalcular al cambiar cantidad. Verificado con la BD real (copia):
2 tramos guardados y precios 150→7 a partir de 2 uds y →6 a partir de 5 uds.

Además, el módulo de actualizaciones elimina los instaladores antiguos tras cada descarga: una descarga
antigua del release v3.82 había vuelto a sobrescribir el JAR instalado con una compilación sin la
persistencia transaccional (causa del error "no se puede grabar el registro" al crear productos).
El target `test` invoca `org.junit.runner.JUnitCore` mediante `<java>`, por lo que **no** requiere
`ant-junit.jar` ni copiar JUnit a `ANT_HOME/lib`.

Nota: Ant recomienda fijar `includeantruntime="false"`; se omite a propósito para mantener compatibilidad
con Ant 1.7.1 (el jar incluido en `lib/`), donde ese atributo no existe.
