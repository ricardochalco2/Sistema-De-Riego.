# Sistema de Riego — Android + Arduino UNO + HC-05

Proyecto inicial funcional (código fuente). **No incluye APK precompilado**.

## Requisitos
- Android Studio con JDK 17, Android SDK 35 y conexión a Internet para descargar Gradle/dependencias.
- Teléfono Android 8.0+ con Bluetooth clásico SPP.
- Arduino UNO, sensor analógico de humedad, módulo HC-05, módulo de relé y bomba con fuente adecuada.

## Abrir y compilar la app
1. Descomprime el ZIP.
2. Abre Android Studio > Open > selecciona la carpeta `SistemaDeRiego`.
3. Espera la sincronización de Gradle. Si Android Studio solicita instalar SDK 35, acepta.
4. Conecta el móvil por USB, activa Opciones de desarrollador y Depuración USB.
5. Pulsa Run (▶) para instalar en el móvil. Alternativamente: Build > Build APK(s); encontrarás el APK en `app/build/outputs/apk/debug/`.
6. Autoriza el permiso Bluetooth en Android 12+.

## Preparar el Arduino
1. Abre `Arduino/SistemaRiegoUNO/SistemaRiegoUNO.ino` con Arduino IDE.
2. Cablea: sensor AO a A0, VCC a 5V y GND a GND; relé IN a D7.
3. HC-05 TXD a D10; Arduino D11 a HC-05 RXD **a través de un divisor resistivo 5V→~3.3V**; GND común; alimentación HC-05 según especificaciones de tu placa.
4. La bomba NO se conecta directamente al pin Arduino: utiliza relé o controlador apropiado y una fuente separada con tensión/corriente correctas. Respeta aislamiento y seguridad eléctrica.
5. Sube el sketch, calibra `dryRaw` y `wetRaw`, y verifica si el relé es activo en LOW o HIGH.
6. Empareja HC-05 desde Ajustes Bluetooth del teléfono (PIN habitual 1234 o 0000 si no fue cambiado).
7. Abre la app, pulsa Conectar y selecciona HC-05.

## Protocolo serie
- App → Arduino: `ON`, `OFF`, `AUTO`, `MANUAL`, `STATUS` seguidos de `\n`.
- Arduino → App: `HUM:45;PUMP:OFF;MODE:AUTO` seguido de `\n`.
- Velocidad HC-05: 9600 baudios (ajustar si tu módulo está configurado diferente).
- ON/OFF pasan a modo manual; MANUAL apaga la bomba; AUTO usa histéresis 35%-50%.

## Notas
- La fecha/hora mostrada es la del teléfono, no la de un reloj RTC del Arduino.
- El historial en terminal solo se conserva mientras la app está abierta.
- El control Bluetooth funciona a corta distancia, sin Internet; no es BLE.
- La bomba se inicializa apagada al reiniciar Arduino. Para uso real añade protección ante falta de agua, límites máximos de riego y un interruptor físico de emergencia.
- Es un punto de partida: antes de dejar la bomba sin supervisión prueba el sistema con cargas seguras y calibra el sensor.

## Compilar gratis en GitHub (sin Android Studio)
1. Crea un repositorio **privado** en GitHub, por ejemplo `SistemaDeRiego`.
2. Sube **el contenido de esta carpeta** a la raíz del repositorio, incluyendo `.github/workflows/compilar-apk.yml` (no subas solo el ZIP como archivo).
3. Abre **Actions > Compilar APK Sistema de Riego > Run workflow**.
4. Cuando termine, abre la ejecución y descarga el artifact **SistemaDeRiego-APK**. Descomprime el archivo descargado para encontrar `app-debug.apk`.
5. Para instalarlo, transfiere el APK al móvil y ábrelo. Autoriza instalar desde esa fuente solamente si confías en el archivo.
6. Para crear un **QR de instalación real**, necesitas primero alojar el APK en un enlace HTTPS público de descarga directa (por ejemplo una publicación de GitHub Releases). Un QR no almacena el APK: almacena la dirección de descarga. No publiques el APK ni su enlace antes de probar y validar los permisos y conexiones.
