# Cubik · Calculadora de Cubicaje y Estiba de Bultos

**Cubik** es una herramienta de patio desarrollada por **KyrnForge** para coteros, despachadores, encargados de bodega y conductores de carga. Permite calcular en segundos cómo armar, distribuir y trabar cargas de bultos (25, 40 o 50 kg) dentro del camión, verificando el límite de peso en báscula y el equilibrio entre los ejes delantero y trasero.

Está diseñada específicamente para trabajo en exteriores bajo sol directo: interfaz clara de alto contraste, tipografía condensada de lectura rápida y botones grandes pensados para usarse con una sola mano o con guantes.

---

## ¿Para qué sirve?

En el cargue manual de bultos (granos, concentrados, cemento, abonos, harinas o carga general), un mal reparto desde las primeras filas provoca tres problemas frecuentes:
1. **Descuadre al cerrar la compuerta**: sobran o faltan bultos al final porque no se calcularon bien los pisos por fila.
2. **Camión recostado**: exceso de peso hacia la cabina o hacia la compuerta, afectando la estabilidad y el pesaje por eje.
3. **Pisos sueltos sin amarre**: remates desiguales en la parte superior que se corren en carretera.

Cubik resuelve esto antes de subir el primer bulto: indica exactamente cuántas filas armar, cuántos pisos completos lleva cada fila, cómo acomodar los bultos sobrantes en el remate superior y genera una **hoja de cargue paso a paso** lista para enviar por WhatsApp a la cuadrilla o al conductor.

---

## Cómo funciona

### 1. Selección de camión y control de báscula
Según la cantidad de bultos y el peso unitario (25, 40 o 50 kg), Cubik calcula el tonelaje total y selecciona automáticamente el vehículo más adecuado que soporte la carga (o permite elegirlo manualmente):

| Vehículo | Capacidad máx. | Largo base | Altura de baranda | Configuración |
| :--- | :---: | :---: | :---: | :--- |
| **Turbo** | 4,5 T | 6 filas | 10 pisos | Eje sencillo |
| **Sencillo** | 9,0 T | 8 filas | 12 pisos | Eje sencillo |
| **Doble troque** | 17,0 T | 9 filas | 12 pisos | Tándem |
| **Mula** | 32,0 T | 12 filas | 13 pisos | Tractomula |

Si la carga supera la capacidad del vehículo seleccionado o sobrepasa la altura de la baranda, la aplicación muestra una alerta visual inmediata indicando el exceso en toneladas o pisos.

### 2. Reparto inteligente por filas y pisos
* **División pareja de cabina a compuerta**: divide el total de bultos entre el número de filas del furgón o carrocería. Los bultos restantes se asignan de a uno empezando desde la cabina.
* **Unificación de remate**: cuando el reparto parejo deja dos tipos distintos de remate superior (por ejemplo, unas filas con 2 bultos sueltos encima y otras con 1), el algoritmo busca una distribución equivalente con diferencia máxima de 2 bultos por fila que deje **un solo tipo de remate** y el resto en pisos completos cerrados, siempre que el centro de gravedad se mantenga equilibrado.
* **Ancho configurable**: por defecto trabaja con planchas de **5 bultos de ancho** (estándar en carrocerías de carga), permitiendo cambiar a 3 o 4 bultos según el ancho del furgón.

### 3. Regla de amarre en el último piso (Remate)
Los bultos que no alcanzan a completar un piso entero se ubican encima de la fila siguiendo criterios prácticos de estiba:
* **1 bulto sobrante**: va **atravesado** en la mitad de la fila.
* **2 bultos sobrantes**: van **atravesados**, uno contra cada pared lateral dejando el centro libre.
* **3 bultos sobrantes**: van **atravesados** y repartidos de forma pareja a lo ancho.
* **4 o más bultos**: se colocan **a lo largo** igual que los pisos inferiores, desde los costados hacia adentro dejando libre el centro.

### 4. Cuatro vistas interactivas
* **Lateral**: corte longitudinal del camión desde la cabina hasta la compuerta mostrando los pisos por fila, los bultos de amarre y el límite de la baranda.
* **Arriba**: planta superior del furgón detallando el ancho de cada fila y la posición de los bultos atravesados.
* **Compuerta / Atrás**: corte transversal fila por fila para ver exactamente cómo se arma cada plancha.
* **Ejes**: gráfico de barras por fila con el cálculo del centro de carga longitudinal (aprobado cuando el reparto adelante/atrás se mantiene dentro del margen del **44% al 56%**).

---

## Cómo usarlo

1. **Bultos a cargar**: ingresa la cantidad total de bultos (con el teclado o los botones `−` / `+`, que aceleran de a 10 al mantenerlos presionados), selecciona el peso por bulto (**25, 40 o 50 kg**) y, si lo deseas, escribe el nombre del producto (por ejemplo: *Arroz*, *Maíz*, *Cemento* o *Carga general*).
2. **Espacio del camión**: deja activada la selección automática según el peso o toca directamente el tipo de camión. Ajusta el largo (de 3 a 16 filas) o el ancho (3, 4 o 5 bultos) si la carrocería tiene medidas particulares.
3. **Modo de ajuste**:
   * **Repartir parejo**: calcula automáticamente la mejor estiba posible.
   * **Ajustar a mano**: habilita controles individuales (`▲` / `▼`) debajo del plano para subir o bajar bultos en cada fila según la necesidad del cargue, indicando en tiempo real si cuadra con el total o cuántos bultos faltan/sobran.
4. **Hoja de cargue**: revisa el resumen superior y las instrucciones numeradas al final de la pantalla. Presiona **Compartir carga por WhatsApp** o **Copiar texto** para enviar la orden de estiba.

### Modo Rápido (calculadora)
Para quien prefiere no recorrer toda la pantalla: el botón de arriba a la derecha cambia entre el modo completo y el **Modo Rápido**, una calculadora que usa el mismo motor de reparto pero guarda su propio estado e historial.

* Se escribe `bultos,filas,ancho` y se toca `=`: por ejemplo `300,8,5`. Filas y ancho son opcionales (si faltan se usan las filas del camión y 5 de ancho). El `+` suma pedidos dentro del total (`120+80+100,8,5`).
* Para corregir, se toca el número en la pantalla (por ejemplo el `8` o el `130`): queda resaltado, se escribe el nuevo y se toca `=`.
* Las teclas **kg** (25 / 40 / 50) y **camión** (Auto, Turbo, Sencillo, Doble, Mula) cambian con cada toque.
* El resultado sale debajo de los números: `4 filas → 38 = 7 pisos de 5 + 3 atrav.`
* **Varios productos con `÷`**: `300,8,5÷130,100,70` dice qué lleva cada producto, cargando desde el fondo (`P1 · 130` → `3 filas de 38 + 16 en fila 4`), y debajo cuántas filas hay de cada tamaño y cómo se arman (`4 filas de 38 = 7 pisos de 5 + 3 atrav.`). Lo que sobra del total pasa solo a ser un producto más, marcado como **RESTO** (`300,8,5÷130` → P2; `300,8,5÷130,100` → P3 con 70). Si los productos pasan del total, se muestra el aviso y no se guarda. Máximo 4 productos.
* **Historial** (botón de arriba a la izquierda): carpetas por día, solo los días en que se calculó algo. Cada cálculo abre sus 4 vistas, WhatsApp y "Usar en calculadora". Se puede borrar un cálculo, un día o todo, y **exportar / importar** un archivo `.json` que sirve igual en la web y en Android para pasar el historial a otro dispositivo.

### Actualizaciones (Android)
Al abrir la app, Cubik revisa si hay una versión nueva en [Releases](../../releases), como mucho una vez cada 4 horas. Si la hay, avisa y, al tocar **Actualizar**, descarga el APK y abre el instalador de Android. La primera vez el teléfono pide permitir que Cubik instale apps.

---

## Estructura del proyecto

El repositorio incluye dos implementaciones independientes que comparten exactamente el mismo motor de cálculo y diseño visual:

```text
cubik/
├── index.html                  # Versión web autocontenida (HTML5 + CSS3 + JS vanilla y gráficos SVG)
└── android/                    # Aplicación nativa Android (Kotlin + Jetpack Compose, sin WebView)
    └── app/src/main/java/com/kyrnforge/cubik/
        ├── MainActivity.kt     # Punto de entrada edge-to-edge
        ├── Model.kt            # Lógica de reparto, báscula, balance de ejes y hoja de cargue
        ├── Drawings.kt         # Motor vectorial en Canvas para las 4 vistas del camión
        ├── Ui.kt               # Interfaz de usuario y controles táctiles de patio
        ├── Store.kt            # Persistencia local automática en SharedPreferences
        ├── Calc.kt             # Modo Rápido: lectura de lo escrito, productos y textos cortos
        ├── History.kt          # Modo Rápido: estado, historial por días y respaldo JSON
        ├── CalcUi.kt           # Modo Rápido: calculadora, historial y detalle
        ├── Updater.kt          # Busca e instala versiones nuevas desde GitHub Releases
        └── Theme.kt            # Paleta industrial y tipografía Barlow / Barlow Condensed
```

---

## Ejecución y compilación

### Versión Web
No requiere servidor ni instalación de paquetes. Basta con abrir el archivo `index.html` directamente en cualquier navegador móvil o de escritorio.

### Versión Android (APK)
Requisitos:
* JDK 17 (incluido con Android Studio)
* Android SDK (`minSdk 28`, `compileSdk 36`)

Para compilar desde la terminal en Windows (PowerShell):

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
cd android
.\gradlew.bat assembleDebug
```

El APK compilado se generará en `android/app/build/outputs/apk/debug/app-debug.apk` (o en `release/` al ejecutar `assembleRelease` con la llave de firma configurada). También puedes descargar el instalador listo para usar directamente desde la sección de [**Releases**](../../releases).

---

## Licencia

Distribuido bajo la licencia **MIT**. Consulta el archivo [LICENSE](LICENSE) para más detalles.

Desarrollado por **KyrnForge**.
