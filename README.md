# VibeTube

Cliente de video para **Android TV**: busca y reproduce videos de YouTube desde el control remoto,
sin la app oficial. Usa el núcleo **MediaServiceCore** de SmartTube y la interfaz
«Flotante minimalista» de VibeM3U. Versión 0.2.0.

- Idioma y región fijos en español de Chile (`es` / `CL`), antes de cualquier pedido.
- Reproductor Media3 con controles propios.

## Aviso

VibeTube es un proyecto independiente. **No está afiliado a YouTube ni a Google**, no usa sus
marcas ni logos y no cuenta con su respaldo. Respeta los términos de servicio de YouTube.

## Componentes

- [MediaServiceCore](https://github.com/yuliskov/MediaServiceCore) (MIT): núcleo de YouTube
  extraído de SmartTube, por yuliskov.
- SharedModules (MIT), también de yuliskov.

Los avisos de copyright y las licencias de terceros están en [`NOTICE`](NOTICE).

## Compilar

Requisitos:

- **JDK 17**. El JDK de Android Studio (JBR) es de versiones más nuevas y Gradle 7.5 no corre con él.
- Android SDK con compileSdk 34. Android Studio crea `local.properties` con `sdk.dir`.
- Los submódulos `MediaServiceCore` y `SharedModules`.

```sh
git clone --recurse-submodules https://github.com/SPxMM3R1/vibetube.git
cd vibetube
```

Si ya clonaste sin submódulos: `git submodule update --init --recursive`.

En Windows (Git Bash), con JDK 17 instalado en su ruta habitual:

```sh
export JAVA_HOME="/c/Program Files/Microsoft/jdk-17.0.20.101-hotspot"
./gradlew.bat :app:assembleDebug
```

En Linux o macOS:

```sh
chmod +x gradlew
./gradlew :app:assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Para instalarlo en un emulador
o en un televisor con depuración por red activa: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Licencia

Código propio bajo licencia MIT, ver [`LICENSE`](LICENSE). Titular: VibeTube contributors.
