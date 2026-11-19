# napa-java

Java bindings for [NAPA](https://github.com/zwatx/NAPA)'s AprilTag detector, packaged for PhotonVision the way
[mrcal-java](https://github.com/PhotonVision/mrcal-java) packages mrcal:

- `org.photonvision:photon-napa-java`: `org.photonvision.napa.NapaJNI`, the bindings.
- `org.photonvision:photon-napa-jni:<platform>`: the `napa_jni` library, one jar per platform (`linuxx86-64`,
  `linuxarm64`, `windowsx86-64`, `osxuniversal`).

NAPA is a git submodule pinned to a release, and its own CMakeLists builds it into `napa_jni`. Load the library (for
example with PhotonVision's `LoadJNI`) before calling `NapaJNI`.

## Building

Needs CMake 3.24+, a C/C++ compiler and JDK 25.

```bash
git submodule update --init
cmake -B cmake_build -DCMAKE_BUILD_TYPE=Release
cmake --build cmake_build
./gradlew build
```

`./gradlew build` packages the library CMake built and runs the tests against it. On Windows, configure as CI does,
with Ninja and clang-cl: `-G Ninja -DCMAKE_C_COMPILER=clang-cl -DCMAKE_CXX_COMPILER=clang-cl`. On macOS, add
`-DCMAKE_OSX_ARCHITECTURES="arm64;x86_64"`.

To try a local build in PhotonVision, publish it with a version and point PhotonVision's `napaVersion` at it:

```bash
./gradlew -PversionString=dev-local publishToMavenLocal
```

## Publishing

CI publishes to PhotonVision's Maven on pushes to `main` and `v*` tags once the `ARTIFACTORY_API_KEY` secret is set;
until then it only builds and tests. The version comes from the latest `v*` tag (`git describe`), so tag a release before
setting the secret, or the first publish is `dev-unknown`.

## Updating NAPA

Check out the new release in the `NAPA` submodule, rebuild, and run the tests. `NapaJNI.java` and `napa_jni.cpp`
follow `NAPA/src/napa_vision/_native/napa_core.h`; CMake generates the JNI header from `NapaJNI.java`.

## License

The bindings' own files carry PhotonVision's GPL-3.0-or-later header; the napa_jni library includes NAPA and is
distributed under GPL-3.0-only (see [LICENSE](LICENSE) and [ExternalLicenses](ExternalLicenses)). The tag families'
codewords come from AprilTag under its BSD license.
