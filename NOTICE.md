# Third-party notices

## SpektraLab Android

This application includes and modifies the SpektraLab Android editor and its
native spectral film engine.

- SpektraLab Android: https://github.com/SAINTTaiYi/SpektraLab-Android
- SpektraLab engine: https://github.com/JamesQiu2005/SpektraLab
- Film modeling upstream: https://github.com/andreavolpato/spektrafilm
- SpektraLab Android integration version: 1.3.4

The integrated code and native engine are distributed under GPL-3.0-or-later.
The film and paper profile data retain the upstream CC BY-SA 4.0 terms.
License texts are in `LICENSES/`.

Modification for this app: the editor is launched as an internal activity
under the MiaoJi package. Its own update check and feedback prompt are skipped
when embedded, so MiaoJi owns application updates. The engine, profiles,
shaders, fonts and native libraries are otherwise shipped from the public
1.3.4 release. All film rendering and export remain local to the phone.

## MiaoJi Paint original code

The original MiaoJi Paint code remains available under the MIT license in
`LICENSES/MiaoJiPaint-MIT.txt`. The combined distributed application is
GPL-3.0-or-later because it includes SpektraLab Android.
