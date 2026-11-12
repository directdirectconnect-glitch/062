# English Words visual device-test APK — 5121 words, 159 images

Status: **not built yet**. This test branch contains the 5121-word dictionary, visual lesson UI and fail-closed offline asset installer. The private 159-image archive is stored on Google Drive, **not yet in this public GitHub repository**.

Approved archive: `EnglishWords_5121_words_159_images_offline_assets.zip` (10,119,307 bytes). Private Drive file ID `1BL-MKqkW-KRp1rBMRXWSZw3hxCibpIc0`.

There are 164 WebP source files in Google Drive, but only 159 images have valid, unique dictionary word IDs. The following 5 lack matching dictionary words and are excluded from the APK package: anchor, binoculars, castle, cookie, rocket. Do not assign another word's ID to these illustrations.

The repository is PUBLIC. Before transferring the archive, confirm whether making it public in a Git commit is acceptable, or use a private repository / authenticated artifact transfer mechanism. Do not expose Drive sharing links or access tokens.

To build after the archive is safely available to Actions, place the ZIP at `integration/EnglishWords_5121_words_159_images_offline_assets.zip` on this branch and trigger `.github/workflows/visual-test-apk.yml`. The workflow verifies the dictionary count, all image IDs and assets, runs unit tests and compiles a debug APK. Artifact name: `EnglishWords-5121-159-Visual-Test-APK`.

Installation: test APK has `.visualtest` application ID suffix; this allows installation beside the existing English Words app without removing the current 0.9.8. Physical S20 FE and Galaxy Fold tests have not yet run. Verify learning mode image display, picture hidden until quiz answer, no placeholder for words without images, voice input/output, airplane mode, and Fold outer/inner layout.

Do not claim an APK has been built until a successful GitHub Actions run and its APK artifact have been verified.
