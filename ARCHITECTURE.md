# Arquitectura de uniCenta oPOS — informe de lectura exhaustiva

> Documento generado tras recorrer línea a línea la totalidad del código fuente y los recursos del
> repositorio. Incluye el estado tras las Fases 1-5 del plan de mejora (`IMPROVEMENT_PLAN.md`).
> Fecha: 2026-11-23. Versión de la aplicación: **3.81**.

---

## 1. Ficha técnica y magnitudes

| Concepto | Dato |
|---|---|
| Versión | **3.82** (añade precios por cantidad; la 3.81 introdujo seguridad y actualizaciones silenciosas) |
| Naturaleza | TPV/POS de código abierto para pantalla táctil, descendiente directo de Openbravo POS 1.x |
| Licencia | GPL v3 (`LICENSE.md`); algunas clases Jasper adaptadas son LGPL |
| Lenguaje | Java 7 (target `1.7`), Swing, JDBC, BeanShell, Velocity, JasperReports, JavaPOS/ESC-POS, RMI, RXTX |
| Módulos fuente | `src-beans` (34 archivos / 5.549 líneas), `src-data` (121 / 12.225), `src-pos` (403 / 79.934), `test` (8 / 364) |
| Total Java | **566 archivos, ~98.100 líneas** |
| Recursos | ~200 `.sql`, 56 `.bs`, 47 `.jrxml`, 526 `.properties`, 144 `.form`, 74 `.xml`, 16 idiomas |
| Artefactos | `unicentaopos.jar`, `unicentaopos_3.81_bin.zip`, `unicentaopos_3.81_src.zip` |
| Build | Ant (`build.xml` importa `build_working.xml`) + CI en GitHub Actions (JDK 8) |

---

## 2. Mapa general: cómo está estructurado el programa

```
unicentaopos.jar  (todos los módulos compilados juntos en build/classes)
│
├── com.openbravo.basic          excepción transversal BasicException
├── com.openbravo.beans          widgets táctiles (teclado, calendario, diálogos, border)
├── com.openbravo.editor         framework de edición tipada + teclado en pantalla
├── com.openbravo.format         Formats (13 estrategias de formato) + validadores
│
├── com.openbravo.data.loader    DAL artesanal: Session, Sentence, Serializer, SessionDB
├── com.openbravo.data.model     metadatos Table/Row/Column/Field/PrimaryKey → SQL CRUD
├── com.openbravo.data.user      capa MVC de mantenimiento (BrowsableEditableData, providers)
├── com.openbravo.data.gui       widgets reutilizables (JFind, JNavigator, JSaver, mensajes)
│
└── com.openbravo.pos            APLICACIÓN
    ├── forms        arranque, sesión, contenedor BeanFactory, menú, DAO del sistema/ventas
    ├── instance     monoinstancia RMI (endurecida en Fase 2)
    ├── scripting    motores BeanShell/Velocity + frontera de confianza (Fase 3)
    ├── config       diálogo de configuración (8 pestañas, ~2.164 líneas la de periféricos)
    ├── updates      módulo de actualización silenciosa (check/descarga/install opcional)
    ├── sales        TPV, bolsa de tickets, restaurante, impuestos, impresión de comandas
    ├── ticket       modelo de ticket, líneas, impuestos, productos
    ├── payment      51 clases: 11 métodos de pago, 9 pasarelas, lectores de banda
    ├── printer      56 clases: ESC/POS, JavaPOS, visores, cajón, impresora fiscal, pantalla
    ├── inventory    44 clases: productos, stock, atributos, impuestos, almacenes
    ├── customers    fichas, deuda, buscador, reservas
    ├── admin        usuarios, roles, permisos, recursos
    ├── epm          fichaje, pausas y bajas de empleados
    ├── reports      base Jasper + cargador asíncrono + parámetros reutilizables
    ├── panels       infraestructura de mantenimiento y cierre de caja
    ├── catalog      parrilla táctil de categorías/productos
    ├── promotion    6 tipos de promoción
    ├── scale        5 drivers de báscula
    ├── scanpal2     protocolo serie del escáner de mano
    ├── mant         salas y mesas
    ├── migrate      migración manual entre motores de BD
    ├── dbupdate     actualizador legacy (desactivado)
    ├── imports      importación CSV de productos
    └── util         criptografía, imágenes, códigos de barras, visor Jaser adaptado
```

**Separación real de capas:** no es un MVC limpio. Es una arquitectura de **four-capas con pegamento
dinámico**: la capa `data` es un micro-ORM propio; la capa `pos` mezcla vista y controlador en `JPanel*`;
y el "inyector de dependencias" es `JRootApp.getBean()`, que resuelve clases por reflexión y scripts
BeanShell desde la base de datos. Los `DataLogic*` son los DAO/servicios.

---

## 3. Arranque y ciclo de vida (fully traced)

1. **`StartPOS.main(String[])`** (todo dentro de `EventQueue.invokeLater`):
   - `registerApp()`: consulta por RMI el objeto `AppMessage` en `127.0.0.1:1099` y llama a
     `restoreWindow(InstanceToken.get())`. Si otra instancia respondió, `System.exit(1)`.
   - `new AppConfig(args).load()` lee `%USERPROFILE%\unicentaopos.properties` (o `args[0]`).
   - Fija `Locale`, los patrones globales `Formats` (moneda/fecha/hora) y el `LookAndFeel`
     (Swing estándar o `SubstanceSkin`) por reflexión.
   - `TicketInfo.setHostname(machine.hostname)`.
   - Según `machine.screenmode`: `JRootKiosk` (undecorated, pantalla completa) o `JRootFrame`.

2. **`JRootFrame/Kiosk.initFrame(props)`** crea `JRootApp` y llama a `initApp(props)`.
   Si falla, abre `JFrmConfig`. Tras el éxito registra `InstanceManager` solo si
   `machine.uniqueinstance=true`, coloca el icono/título y muestra la ventana.

3. **`JRootApp.initApp(props)`** (~1.000 líneas) — el verdadero bootstrap:
   - `AppViewConnection.createSession(props)`: valida `db.driverlib`, carga el driver desde un
     `URLClassLoader`, descifra `db.password` (`crypt:` → `AltEncrypter`) y crea `Session`.
   - Obtiene `DataLogicSystem` vía `getBean()` (validado por `BeanNameValidator`).
   - Compara `findVersion()` con `APP_VERSION=3.81`; si difiere ejecuta
     `<Motor>-create.sql` o `<Motor>-upgrade-<versión>.sql` desde `/com/openbravo/pos/scripts/`
     mediante `BatchSentenceResource` (sustituye `$FILE{}` y `$PARAM{}`).
   - Carga propiedades por host (`<host>/properties` en la tabla `RESOURCES`), crea/recupera la
     **caja activa** (UUID + secuencia) y el almacén (`location`, por defecto `"0"`).
   - Inicializa `DeviceTicket`, `TicketParser`, `DeviceScale`, `DeviceScanner` y el reloj del título.
   - `showLogin()`: selector de usuarios, lector de tarjeta por `KeyListener`, mensaje en el visor.

4. **Login:** `AppUser.authenticate()` (sin password) o `JPasswordDialog` + `Hashcypher.authenticate`.
   Tras un login correcto, `upgradePasswordIfNeeded()` rehashea contraseñas legacy a PBKDF2 y lo
   persiste con `DataLogicSystem.execChangePassword`. `openAppView()` crea `JPrincipalApp`.

5. **`JPrincipalApp`** construye el menú ejecutando `Menu.Root` con BeanShell, pero **solo después**
   de que `ScriptSecurity.resolve()` verifique que el script de la BD coincide con la plantilla
   versionada. Cada entrada se filtra por `AppUser.hasPermission(...)` (XML de `ROLES.PERMISSIONS`).
   `showTask()`/`executeTask()` resuelven la vista con `AppView.getBean()` y la montan en un
   `CardLayout` junto al panel de menú.

6. **Cierre:** `JRootApp.tryToClose()` limpia el visor, cierra la `Session` y hace `dispose()`.

---

## 4. Patrones de diseño identificados

| Patrón | Dónde |
|---|---|
| **Session + Strategy** | `Session` (una `Connection`) delega el dialecto a `SessionDB{Derby,MySQL,PostgreSQL,Oracle,HSQLDB,Generic}` elegido por `DatabaseMetaData` |
| **Command + Template Method** | `BaseSentence` define `exec/list/find` como `final`; `StaticSentence`, `PreparedSentence`, `MetaSentence`, `BatchSentence`, `SequenceFor*` implementan `openExec/closeExec` |
| **Serializer / micro-ORM** | Pares `SerializerRead`/`SerializerWrite` (funcionales) y `SerializableRead`/`SerializableWrite` (la entidad se auto-mapea). Implementaciones singleton por tipo escalar + `...Builder` que crea una instancia por fila |
| **Factory / IoC casero** | `JRootApp.getBean()` con caché `Map<String,BeanFactory>`, `BeanFactoryCache`, `BeanFactoryData`, `BeanFactoryDataSingle`, `BeanFactoryScript` |
| **MVP light** | `AppView`/`AppUserView` (controller) + `JPanelView` (vista) + `DataLogic*` (servicio) |
| **MVC de mantenimiento** | `BrowsableEditableData` (controlador con estados `ST_INSERT/UPDATE/DELETE`), `BrowsableData` (ListModel), `EditorRecord` (vista), `JNavigator`/`JSaver`/`JCounter` (observadores vía `BrowseListener`/`StateListener`/`DirtyListener`) |
| **Strategy/Flyweight** | `Formats` con 13 singletons; `Datas` con 10 tipos; `QBFCompareEnum` con 10 comparadores |
| **Observer** | `JNumberEvent`, `PropertyChange("Date"/"Edition"/"image")`, `EventListenerList` |
| **Composite / Null Object** | `EditorCreatorComposed`, `SerializerWriteComposed`, `CompoundIcon`; `DevicePrinterNull`, `NullIcon`, `DocumentLoaderBasic`, `Formats.NULL` |
| **Adapter** | `JDBCDataResultSet`, `SentenceUpdateResultSet`, `DriverWrapper`, `JRDataSourceBasic`, `PrintableBasicTicket` |
| **Builder** | `NormalBuilder`, `QBFBuilder`, `TableDefinition` (CRUD genérico), `ThumbNailBuilder` |
| **Fachada** | `DeviceTicket` (todos los periféricos), `Hashcypher` (autenticación), `AppConfig` (configuración) |

---

## 5. La capa de datos (`src-data`) en detalle

### 5.1 Flujo de una consulta

```
DataLogic*.getXxx()
  → Sentence.list()/find()/exec()            [BaseSentence, métodos final]
  → openExec(params)
       ├─ PreparedSentence: PreparedStatement + SerializerWrite.writeValues(PreparedSentencePars)
       │    y SerializerRead para cada fila        ← VÍA SEGURA (parámetros enlazados)
       └─ StaticSentence: ISQLBuilderStatic.getSQL() → Statement.execute(sql)
            ├─ NormalBuilder: sustituye ? por literales formateados
            └─ QBFBuilder: sustituye ?(QBF_FILTER) por (campo LIKE '...' AND ...)
  → JDBCDataResultSet envuelve java.sql.ResultSet
  → fetchAll/fetchPage/fetchOne  → SRS.getCurrent() → SerializerRead.readValues(DataRead)
  → DataRead.getInt/getString/... (1-based) → DTO / Object[]
  → SRS.close() + closeExec()
```

Tras la Fase 5, **28 sentencias** de `DataLogicSystem`/`DataLogicSales` pasaron de `StaticSentence` a
`PreparedSentence`. Quedan las que usan `QBFBuilder` (incompatibles con `PreparedSentence` sin reescribir
el builder) y el `m_dummy`.

### 5.2 Transacciones

`Session.begin()` desactiva `autoCommit`; `Transaction<T>.execute()` sigue el patrón "si ya hay
transacción, únete; si no, begin→transact→commit", con rollback ante `BasicException`.
`SentenceExecTransaction` envuelve operaciones compuestas (por ejemplo `saveTicket`: inserta
`RECEIPTS`/`TICKETS`/`TICKETLINES`/`PAYMENTS`/`TAXLINES`, descuenta stock y actualiza deuda).
Limitaciones: una sola conexión no thread-safe, sin savepoints, rollback solo para `BasicException`,
y `BaseSentence` no usa `try/finally` (fuga de `ResultSet` si falla el fetch).

### 5.3 Estrategias por motor

| Motor | TRUE/FALSE | Secuencia | Observaciones |
|---|---|---|---|
| Derby (por defecto) | 1/0 | `DELETE`+`INSERT`+`IDENTITY_VAL_LOCAL()` | No concurrente; BD embebida en `user.home/unicentaopos-database` |
| MySQL | TRUE/FALSE | `UPDATE ... LAST_INSERT_ID()` | Atómica por conexión |
| PostgreSQL | True/False | `NEXTVAL('seq')` | `RESTART WITH '0'` dudoso |
| Oracle | 1/0 | `seq.NEXTVAL FROM DUAL` | El "reset" solo avanza |
| HSQLDB | TRUE/FALSE | `CALL NEXT VALUE FOR seq` | El "reset" solo avanza |
| Generic | TRUE/FALSE | `NEXTVAL('seq')` | Fallback |

### 5.4 Modelo de datos

46 tablas en `Derby-create.sql`: `APPLICATIONS`, `ROLES`, `PEOPLE`, `RESOURCES`, `CUSTOMERS`,
`CATEGORIES`, `TAXCATEGORIES`, `TAXES`, `ATTRIBUTE*`, `PRODUCTS*`, `LOCATIONS`, `STOCKDIARY`,
`STOCKLEVEL`, `STOCKCURRENT`, `CLOSEDCASH`, `RECEIPTS`, `TICKETS*`, `TICKETLINES`, `PAYMENTS`,
`TAXLINES`, `FLOORS`, `PLACES`, `RESERVATIONS*`, `THIRDPARTIES`, `SHAREDTICKETS`, `SHIFTS`,
`LEAVES`, `BREAKS`, `MOORERS`, `CSVIMPORT`, `DRAWEROPENED`, etc. Las relaciones están en el DDL y
en los `JOIN` escritos a mano en los DAO; **no hay entidades JPA ni foreign keys modeladas en Java**
(aparte de los scripts `-DropFK`/`SQL-CreateFK` del migrador).

---

## 6. Subsistema `beans`: la capa de UI táctil

- **`Formats`** es el servicio global de formato/parseo (13 estrategias singleton) configurado desde
  `StartPOS` con los patrones del `.properties`.
- **`JEditorAbstract` → `JEditorNumber`/`JEditorText` → editores concretos** implementan el framework
  de entrada táctil; `JEditorKeys` es el teclado en pantalla con modos string/double/integer.
  *Nota:* `JEditorNumber` depende de `AppConfig`/`AppLocal` (capa superior) — acoplamiento inverso.
- **`JNumberKeys`/`JPasswordDialog`/`JCalendarDialog`** son los diálogos modales reutilizados por
  básculas, login y edición.
- **`LocaleResources`** fusiona bundles con fallback `** clave **`; base de `AppLocal`, `LocalRes`.
- Deuda: `Vector`/`Enumeration` raw, `Calendar` estático compartido, textos en inglés hardcodeados.

---

## 7. Subsistema `pos`: la aplicación

### 7.1 `forms` — el núcleo

- `AppConfig`: 60 claves por defecto (Derby embebido, LAF Metal, `screenmode=window`,
  `ticketsbag=standard`, `payment.gateway=external`, tamaños de papel, `machine.uniqueinstance`…).
- `AppUser`: permisos desde XML, ~60 renombrados de clases legacy, `authenticate()` con hash migrado.
- `JRootApp` (1.022 líneas): bootstrap, login, `getBean()` con whitelist, cierre.
- `JPrincipalApp` (636): menú scriptable, permisos, `CardLayout`, cambio de contraseña.
- `DataLogicSales` (1.742): catálogo, promociones, tickets, impuestos, caja, deuda, stock.
- `DataLogicSystem` (648): versión, recursos, roles, caja, CSV, deduplicación de productos.
- `BeanFactory*`: contenedor IoC propio; `BeanNameValidator` limita qué se puede instanciar.

### 7.2 Flujo de venta completo (end-to-end)

1. `JPanelTicket.init()` obtiene beans, construye `JTicketLines` (columnas Velocity desde `Ticket.Line`),
   `JPanelButtons` (botones/eventos desde `Ticket.Buttons`), la bolsa y el catálogo.
2. `setActiveTicket()` fija usuario/caja/fecha; en restaurante escribe `CUSTOMER`, `WAITER`, `TICKETID`
   en `PLACES`; ejecuta el evento BeanShell `ticket.show`.
3. **Añadir líneas:** por teclado (`stateTransition`: dígitos, `*`, `+`/`-`, `§` báscula, EAN-8/13
   con prefijos 20-28, `c` tarjeta cliente) o por catálogo. `TaxesLogic` resuelve el impuesto
   (cascada + categoría fiscal del cliente). Eventos `ticket.addline` → `ticket.change`.
   *Convención crítica:* si un evento devuelve un valor no-nulo, la operación se **cancela**.
4. **Totales:** `TaxesLogic.calculateTaxes(ticket)` agrupa por impuesto en `TicketTaxInfo`.
5. **Cobro:** `closeTicket()` → `warrantyCheck()` → `ticket.total` → total en visor →
   `JPaymentSelectReceipt/Refund` (efectivo, cheque, paper, tarjeta, free, debt, banco) →
   `ticket.save` → `DataLogicSales.saveTicket()` (número de ticket, stock, deuda) →
   `ticket.close` → impresión `Printer.Ticket`/`Ticket2` con Velocity + `TicketParser` →
   limpieza de mesa en restaurante → nueva venta (y auto-logoff si configurado).
6. **Cocina:** `script.SendOrder` vía BeanShell + tabla `KITCHENDISPLAY`.

`JPanelTicket` es la **God class** del sistema (2.613 líneas): UI, máquina de estados de entrada,
fiscalidad, scripting, impresión, JDBC indirecto y configuración.

### 7.3 Bolsa de tickets

| Modo (`machine.ticketsbag`) | Clase | Persistencia |
|---|---|---|
| `standard` | `JTicketsBagShared` | `SHAREDTICKETS` (UUID); "tomar" un ticket = leer + borrar |
| `simple` | `JTicketsBagSimple` | Ninguna; cadaactivate crea un ticket nuevo |
| restaurante | `JTicketsBagRestaurantMap` (845 líneas) | `SHAREDTICKETS` con id = id de mesa + `PLACES` |
| edición/devolución | `JTicketsBagTicket` | Tickets guardados; prepara copias `RECEIPT_REFUND` |

### 7.4 Pagos (51 clases)

- `JPaymentInterface` → 11 pantallas; `JPaymentSelect` (807 líneas) orquesta pestañas y pagos
  parciales acumulados en `PaymentInfoList`.
- `PaymentInfo`: efectivo, free, ticket (cheque/paper/bank/debt/refunds) y magcard (3 pistas,
  detección de marca por prefijo, Luhn).
- Lectores: `Intelligent` (TAB/LF), `Generic` (autómata ABA), `Keyboard` (tecleo) o pasarela externa.
- Pasarelas (`PaymentGatewayFac`): external, Authorize.Net AIM, BluePay (2), La Caixa/Sermepa,
  Planetauthorize, LinkPoint/First Data (PKCS12), PayPoint/SecPay (SOAP Axis), PaymentsGateway.net.
  Secretos cifrados en el `.properties` con `crypt:` (ahora AES-GCM v2).

### 7.5 Impresión (56 clases)

- `DeviceTicket` es la fachada: lee `machine.printer[.2..6]`, `machine.display`,
  `machine.fiscalprinter` con sintaxis `tipo:param1,param2`.
- `TicketParser` (SAX) interpreta el DTD `ticket.dtd`: `<ticket>`, `<line>`, `<text>`, `<image>`,
  `<barcode>`, `<display animation=...>`, `<opendrawer>`, `<play>`, `<fiscalreceipt/zreport/xreport>`.
- Destinos: pantalla (`JTicket`/`JTicketContainer`), impresora del SO (`javax.print`),
  ESC/POS (Epson/Ithaca/TMU220/Star/SurePOS + `UnicodeTranslator*`), JavaPOS, fiscal, archivo/serie.
- Visor con 5 animadores (flyer, scroll, blink, curtain, null) sobre `DeviceDisplayBase` (Timer 50 ms).
- Cajón: ESC/POS y JavaPOS lo abren realmente; los demás hacen `beep()` y registran `DRAWEROPENED`.

### 7.6 Reportes

`JPanelReport` (base) → `PanelReportBean` (configurable desde `.bs`). Cada reporte tiene un `.bs` que
construye el bean con título, ruta, bundle, sentencia SQL y `JParams*`. `JasperReportLoader` (Fase 4)
carga `.ser` o compila `.jrxml`, y ahora se ejecuta en un `SwingWorker`. `JRDataSourceBasic` puentea
`BaseSentence` → Jasper. El visor es una copia adaptada de `JRViewer` (LGPL, 2.302 líneas).

### 7.7 Módulo de actualizaciones silenciosas (`pos.updates`)
- **Pestaña "Actualizaciones"** en el diálogo de configuración (`JPanelConfigUpdates`), con
  activación, modo silencioso, instalación automática opcional, URL del servidor, intervalo y
  directorio de descarga. El botón "Buscar ahora" usa un `SwingWorker`: toda la comprobación y
  descarga ocurren fuera del EDT y el resultado se muestra solo en una etiqueta (sin diálogos).
- **`UpdateService`** consulta por HTTPS el endpoint de GitHub "latest release" (sin librerías
  externas), compara versiones numéricamente (ignorando sufijos como `-hardened-1`, para no
  ofrecer siempre la misma release) y descarga el instalador a `~/.unicentaopos-updates`.
- **Instalación diferida:** Windows no puede sobrescribir los JAR mientras el POS está abierto
  (instalación inmediata devuelve exit=5). Por eso tanto el botón manual como el check automático
  escriben un `install-pending.cmd` que espera a que termine el PID actual y luego ejecuta el
  instalador con `/VERYSILENT /SUPPRESSMSGBOXES /NORESTART` en el directorio de la aplicación;
  el script se autodestruye. Verificado de extremo a extremo con un directorio temporal.
- **`StartPOS`** inicia un hilo daemon que comprueba silenciosamente al arrancar y cada N horas;
  todos los resultados se registran en `java.util.logging`. Por seguridad, `update.enabled=false`
  por defecto y la URL solo acepta HTTPS.

### 7.8 Precios por cantidad (tramos de descuento)
- Nueva tabla `PRODUCTS_PRICEBREAKS(ID, PRODUCT, MINQUANTITY, PRICESELL)` en los 5 motores, creada
  por los `*-create.sql` y por los `*-upgrade-3.81.sql` (versión 3.82). Sin FK para permitir guardar
  los tramos antes de que exista la fila de producto en altas.
- `ProductsEditor` añade la pestaña **"Precios por cantidad"** (`JPanelPriceBreaks`): check de
  activación, tabla de tramos "cantidad desde → precio", alta/baja de líneas. Se carga en
  `writeValueEdit`.
- **Persistencia:** `createValue()` devuelve 26 posiciones: las 25 columnas de `PRODUCTS` y, en la 25,
  la lista de tramos. `getProductCatInsert()` / `getProductCatUpdate()` guardan los tramos con
  DELETE+INSERT **dentro de la misma transacción** del producto, de modo que un fallo revierte todo y
  el editor muestra el error. Guardado verificado contra una copia de la BD real.
- `JPanelTicket` guarda el precio base en la propiedad `product.pricebase` de la línea, para que
  `JProductLineEdit` recalcule los tramos al cambiar cantidad sin depender de la vista obsoleta
  `getProductInfo` (que tenía un `GROUP BY` inválido, corregido).
- `ProductPricing.getUnitPrice(base, unidades, tramos)` aplica el **precio más bajo** entre los
  tramos cuya cantidad mínima esté cubierta; el precio base actúa como techo (un tramo nunca encarece).
- Se aplica al crear líneas en `JPanelTicket.addTicketLine(...)` y al cambiar la cantidad en
  `JProductLineEdit` (con guardia anti-bucle al sincronizar los editores de precio).
- 6 tests JUnit (`ProductPricingTest`) + validación del DDL en Derby.

### 7.9 Periféricos, presencia y mantenimiento

- Básculas: Casio PD1, Samsung ESP, serie genérica, diálogo y falsa (`DeviceScale` según `machine.scale`).
- Escáner ScanPal2: protocolo serie READ/CIPHER/ACK/OVER para descargar conteos y subir catálogo.
- EPM: `SHIFTS`/`SHIFT_BREAKS`/`BREAKS`/`LEAVES`, fichaje del usuario y control de de bajas.
- Inventario: 44 clases con `JPanelTable`/`JPanelTable2` + `EditorRecord` + `TableDefinition`.
  `ProductsEditor` (1.587 líneas) gestiona 25 campos de producto con managers de precio/margen.
- Clientes: ficha de 25 campos, deuda, reservas y un esqueleto incompleto para pedidos mobile.
- Administración: PEOPLE/ROLES/RESOURCES, permisos XML, editor de recursos texto/imagen/binario.

---

## 8. Internacionalización y scripts

- 16 carpetas de idioma en `locales/` (Albanian, American, Arabic, Argentinian, Brazilian,
  Croatian, Dutch, English, Estonian, French, German, Italian, Mexican, Portuguese, Slovenian, Spanish),
  cada una con `pos_messages_xx`, `erp_messages`, `data_messages`, `beans_messages` y los bundles de
  reportes. Inglés UK por defecto.
- **BeanShell** se usa en: menú (`Menu.Root`), eventos de ticket (`script.*`), botones (`payment.cash`),
  definiciones de reportes (`.bs`) y `BeanFactoryScript` (carga desde classpath, por tanto confiable).
- **Velocity** se usa en plantillas de ticket/visor y celdas de `JTicketLines`.
- Tras la Fase 3, `ScriptSecurity` compara cada script de la BD con su plantilla embebida en
  `/com/openbravo/pos/templates/`: si difiere, se usa la original; si no hay original, se rechaza salvo
  `-Dunicenta.scripts.allowdb=true`. Las credenciales de BD ya no se entregan a los scripts.

---

## 9. Seguridad: estado actual

| Riesgo histórico | Estado |
|---|---|
| SHA-1 sin sal para passwords | **Resuelto**: PBKDF2-HMAC-SHA256, 120k iteraciones, sal de 16 B, migración transparente al login |
| 3DES-ECB con clave derivada del usuario | **Resuelto**: AES-128-GCM v2 con secreto local en `~/.unicentaopos.key`; 3DES solo para leer valores antiguos |
| RMI expuesto en 0.0.0.0 | **Resuelto**: `127.0.0.1` + token por usuario |
| BeanShell desde la BD = RCE | **Mitigado**: verificación contra plantilla + whitelist de clases + `dbPassword` redactado |
| `\'` en escapes SQL | **Corregido**: `'` → `''` (estándar SQL) |
| Driver JAR arbitrario desde properties | **Resuelto**: validación de ruta dentro del directorio de aplicación |
| SQL con datos en logs INFO | **Corregido**: nivel FINE |
| `StaticSentence` con literales | **Parcial**: 28 convertidas a `PreparedSentence`; quedan `QBFBuilder` y SQL crudo en varias clases |
| Contraseña hardcodeada en `PropertyUtils` | **Pendiente** (placeholder heredado, usado por `PromoEditor`) |

⚠️ Operativamente: respaldar `~/.unicentaopos.key` y `~/.unicentaopos.instance` junto con el
`.properties`; sin la clave los valores `v2` son irrecuperables.

---

## 10. Calidad y deuda técnica

**God classes:** `JPanelTicket` 2.613, `JRViewer300` 2.302, `JPanelConfigPeripheral` 2.164,
`DataLogicSales` 1.742, `ProductsEditor` 1.587, `JPanelCSVImport` 1.521, `PromoEditor` 1.540,
`JPaneldbMigrate` 1.339, `CustomersView` 1.212, `JRootApp` 1.022, `PaymentsModel` 1.082.

**Duplicación / código muerto detectado y parcialmente limpiado:**
- Eliminados: `TicketLineInfo1`, `SalesDetailInfo_1`, `TicketsBagRestaurantMap` (stub) y `dlSales.java`.
- Conservados por uso real: `PaymentInfoCash_original`, `JRViewer300`, `JRPrinterAWT300`.
- Duplicaciones vivas: impresión preview/real, `executeEvent` vs `executeEventAndRefresh`, 7 bloques
  de reset de pickup ID, `getWindow()` recursivo en 4 diálogos, mapas de renombrado legacy duplicados.

**Bugs latentes identificados durante la lectura (no corregidos):**
- `JTicketCatalogLines.removeActionListener()` llama a `addActionListener`.
- `ReceiptSplit.accepted` no se reinicia al reabrir.
- `m_KitchenPrint` se oculta justo después de habilitarse por permiso.
- `PromoInfo` tiene varios setters con asignación invertida.
- `TaxCategoriesPanel` usa `TaxCustCategoriesEditor`.
- `RoleTreeInfo.getKey()` lanza `UnsupportedOperationException`; `JRolesTree` no está cableado.
- `DeviceScale` mapea `dialog1` a `ScaleComm` en vez de `ScaleDialog`.
- `SessionDBMySQL.resetSequenceSentence` genera SQL inválido; HSQLDB/Oracle no resetean.
- `DataLogicPresenceManagement.m_isonbreak` no filtra por turno.
- `QBFCompareEnum.COMP_RE` y `COMP_EQUALS` comparten código `3`; `COMP_RE` no escapa `%`/`_`.
- `NormalBuilder` sustituye `?` incluso dentro de literales/comentarios.
- `InactivityListener` interpreta segundos como milisegundos.
- `TicketInfo.printId()` recarga properties en cada llamada; `HostInfo` descarta el parámetro `money`.
- SQL concatenado en `RestaurantDBUtils`, `JMooringDetails`, `PromoEditor`, `KitchenDisplay`,
  `JProductAttEdit` y `ProductsEditor.setCurrentStock()` (este último con credenciales de prueba).

**Convencciones:** comentarios de parches (`JG`, `John L`, `JDL`, `TSirwani`) sin limpiar, `double`
para importes, `List`/`ArrayList` raw, `new Integer(...)`, `System.out.println`, `JOptionPane` en la
lógica de negocio, `System.exit` en utilidades, y ~500 líneas de `initComponents` generado por
NetBeans imposibles de testear.

---

## 11. Recursos y configuración (detalle)

### 11.1 Reportes Jasper
- **47 `.jrxml` + 56 `.bs` + 42 `.properties`** en `reports/com/openbravo/reports/`; 9 `.bs` no tienen
  JRXML propio (reutilizan otro diseño) y **ningún JRXML queda sin `.bs`**.
- Cada `.bs` sigue el mismo esqueleto: crea un `PanelReportBean`, fija título/ruta/bundle,
  construye una SQL con `String` + el marcador `?(QBF_FILTER)`, añade parámetros (`JParamsDatesInterval`,
  `JParamsCustomer`, `JParamsLocationWithFirst`, `ProductFilter`, `JParamsReason`) y campos `Datas.XXX`.
  Ejemplo: `productsales.bs` suma unidades e importes de `TICKETLINES` agrupando por producto.
- Variantes por motor: `categorysalesderby.bs`/`categorysalesmysql.bs`, `top10sales.bs`
  (`FETCH FIRST 10 ROWS ONLY`) vs `top10salesMySQL.bs` (`LIMIT 10`), `productsalesprofit_1.bs`.
- Los `.jrxml` llevan `<queryString>` de diseño (iReport) que **no se ejecuta en runtime**: el panel
  pasa un `JRDataSourceBasic` con los resultados de la `.bs`. Solo 4 tienen gráficos (barra 3D,
  tarta 3D, series temporales, barra); no hay subreports.
- Anomalías: `performancereport.bs` no instancia el bean; `badprice.bs` apunta al reporte
  `newproducts`; `taxcatsales_message(s).properties` duplicados; `cherry.jpg` huérfana.

### 11.2 Localización
- 4 bundles en la raíz (`beans`, `data`, `erp` —obsoleto—, `pos` de 679 líneas) y **16 carpetas de
  idioma con 517 `.properties`** (513 en subcarpetas). Sufijos: `es`, `es_AR`, `es_MX`, `pt`, `pt_BR`,
  `it`, `fr`, `de`, `nl`, `hr`, `et`, `ar`, `en_US`, `al_SQ`; English y Slovenian usan los nombres
  sin sufijo (bundles por defecto).
- **Idioma por defecto: español (es_ES).** `AppConfig.loadDefault()` fija `user.language=es`,
  `user.country=ES`; `StartPOS` llama a `ensureDefaultSpanish()`, que migra una vez las instalaciones
  antiguas (marcador `locale.migration=es-3.81`) y guarda el `.properties`. Se añadieron variantes
  `*_es.properties` de los 4 bundles en `locales/` y 39 bundles de reportes en
  `reports/com/openbravo/reports/`, manteniendo los ingleses como fallback. El usuario puede cambiar
  el idioma en Configuración → Locale.
- Cada idioma tiene `locales/<bundle>_<sufijo>.properties` y `reports/com/openbravo/reports/*_messages_<sufijo>.properties`.
  Brazilian y Portuguese solo traen los 4 bundles de aplicación; Arabic tiene los 4 directamente en
  la carpeta (sin el subdirectorio `locales/`).
- `README_Locales.txt` describe cómo copiar los archivos y elegir Configuración > Locale.

### 11.3 Scripts SQL y migraciones
- **100 `.sql`** en `src-pos/com/openbravo/pos/scripts/`: `*-create.sql` para 5 motores (46 tablas;
  PostgreSQL tiene 42 porque usa `SEQUENCE` en lugar de tablas de numeración) y `*-upgrade-<versión>.sql`.
- `BatchSentence` los parsea línea a línea, ignora `--`, detecta `;` y sustituye `$FILE{...}` (bytes de
  un recurso) y `$PARAM{...}` por parámetros enlazados. `JRootApp` elige el script comparando
  `APPLICATIONS.VERSION` con `3.81`.
- **La migración es estrictamente secuencial**: de 3.70 → `upgrade-3.70.sql`, luego 3.80 →
  `upgrade-3.80.sql`. Existen dos tipos: "recarga completa" (recrea `RESOURCES` y tablas EPM, ~11 KB)
  e incrementales pequeños (columnas nuevas, versión). `SQL-CreateFK.sql` concentra 43 foreign keys;
  `*-DropFK.sql` son sus inversos para el migrador.
- Incidencias: cabeceras desalineadas, referencias a plantillas inexistentes (`script.script.ServiceCharge.txt`,
  `script.ReceiptConsolidate.xml`), y el instalador 3.81 sigue citando `RN_380.txt`.
- `JPaneldbMigrate` solo migra a MySQL/PostgreSQL copiando ~39 tablas y preservando secuencias.

### 11.4 Plantillas (`/com/openbravo/pos/templates/`)
- **79 archivos**: 32 `.xml`, 20 `.txt`, 26 `.png`, 1 sin extensión. Se siembran en la tabla
  `RESOURCES` al crear la BD y se recuperan por nombre.
- Los "XML" son en realidad cuatro cosas distintas:
  1. **Plantillas Velocity de impresión** (`Printer.Ticket.xml` 195 líneas, `TicketNew`, `TicketKitchen`,
     `CloseCash`, `PartialCash`, `FiscalTicket`, `TicketPreview`, …): `<output>` con `<ticket>`,
     `<display>`, `<opendrawer/>`, expresiones `#foreach ($ticketline in $ticket.getLines())`.
  2. **Permisos por rol** (`Role.*.xml`): `<permissions><class name=.../></permissions>` en `ROLES.PERMISSIONS`.
  3. **BeanShell con extensión `.xml`** (`event.change.xml` 298 líneas con 5 tipos de promoción,
     `event.addline.xml`, `Ticket.Close.xml`, …).
  4. **Configuración de UI** (`Ticket.Buttons.xml`, `Ticket.Line.xml`).
- Los `.txt` son BeanShell: `Menu.Root.txt` (153 líneas, define todo el menú), descuentos, stock,
  `script.SendOrder`, `payment.cash.txt`, etc. `ticket.change.txt` **contiene credenciales embebidas**
  (`new Session("jdbc:mysql://...", "root", "pass123")`) — las plantillas versionadas conservan el
  código original aunque en runtime `dbPassword` llega redactado.

### 11.5 Build, arranque y documentación
- `build.xml` importa `build_working.xml` (460 líneas, versión 3.81, `default=dist`): compilación
  unificada de los 3 módulos, `jar` con `Main-Class`, ZIPs bin/src, targets de test con JUnitCore y
  targets de instaladores BitRock/JWS. `build_original.xml` es el histórico (3.01, Java 1.6).
- `start.bat/sh` y `configure.bat/sh`: classpath con ~25 JARs (`lib/`), `locales/`, `reports/` →
  `StartPOS` / `JFrmConfig`; `start.sh` detecta el SO. `migrate.sh` está corrupto (todo en una línea
  y `$DIRNAME/libjasperreports...` sin `/`). `ResetSeq.bat` → `JResetPickupID`.
- **Instalador Windows:** los targets BitRock originales (`installer.pos.*`) requieren un `builder`
  propietario y un `installer/installer.xml` que no está en el repo, por lo que no generaban nada.
  Se añadió `installer/unicentaopos.iss` (Inno Setup 6, gratuito) y los targets `stage.installer`,
  `installer.windows` y `release`. El instalador se instala sin elevación (`PrivilegesRequired=lowest`,
  directorio por defecto `%LOCALAPPDATA%\uniCenta oPOS`), crea accesos directos y desinstalador, y
  fue verificado con `/VERYSILENT` (827 archivos, incluidos los 517 `.properties` de locales).
- Documentación: `README` (guía de arranque v3.81), `README.md` (changelog; v3.90 sin fuente),
  `README_First.txt`, `README_Locales.txt`, `LICENSE.md` (GPL v3), `docs/` (6 PDFs + esquema HTML) y
  22 `Release Notes/RN_*.txt` de 2.40 a 4.00 (RN_400 fechado antes que RN_381: inconsistencia).

---

## 12. Estado del plan de mejora

| Fase | Estado | Resumen |
|---|---|---|
| 0 — Entorno | ✅ | JDK 8 + Ant instalados; build verificado |
| 1 — Seguridad básica | ✅ | PBKDF2, escape SQL, 2 bugs, logging, 16 tests |
| 2 — Credenciales/periféricos | ✅ | AES-GCM, RMI loopback+token, driver validado, 6 tests |
| 3 — Scripts | ✅ | ScriptSecurity, sin dbPassword en scripts, whitelist, 10 tests |
| 4 — Calidad | 🔶 | Helpers extraídos, duplicados eliminados, SwingWorker, CI, 6 tests. Pendiente: dividir God classes |
| 5 — Datos | 🔶 | 28 sentencias migradas a PreparedSentence. Pendiente: QBFBuilder, constraints, pool, Flyway |

**Resultado verificado:** `ant -f build.xml clean test dist` → BUILD SUCCESSFUL, **38/38 tests**.

---

## 13. Conclusiones

uniCenta oPOS es un sistema **funcional, extensible y muy completo en funcionalidad POS** (multi-BD,
16 idiomas, 9 pasarelas, impresión ESC/POS y JavaPOS, restaurante, stock, EPM, reportes), construido
sobre una arquitectura **heredada y artesanal**: micro-ORM propio, contenedor IoC por reflexión y
BeanShell como pegamento. Su mayor riesgo no era funcional sino de **confianza**: casi toda la lógica
ejecutable podía venir de la base de datos. Las fases 1-3 cerraron ese frente sin romper la
compatibilidad con instalaciones antiguas; las fases 4-5 continúan reduciendo deuda con cambios
pequeños y verificados. La refactorización que queda (God classes, QBF parametrizado, migraciones
versionadas) es de gran envergadura y debe hacerse con tests de caracterización y prueba en terminal
real, no en una sola pasada.
