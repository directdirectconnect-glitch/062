# English Words 0.9.8 — 5119 words + 118 offline images (device test)

Test branch: `test/0.9.8-5119-118-images-s20fe-fold`. The existing 0.9.8 release is unchanged.

## Current state
- Dictionary: 5119 stable word IDs from the 0.9.8 release.
- Image pack: audited 118 WebP assets, externally stored in private Google Drive.
- Image display code is connected in MainActivity; images appear in learning mode and only **after answering** in test mode.
- Settings include "Show word illustrations"; absent assets do not block lessons.
- Debug application ID suffix is `.visualtest` so it can be installed alongside the production app.

## One required transfer before the APK can be built
Place the existing file `EnglishWords_118_images_APK_integration.zip` into this branch at:
`integration/EnglishWords_118_images_APK_integration.zip`

The approved package is stored privately in Google Drive folder:
`English Words/Images/APK_Integration_118`.
The archive is about 7.9 MB. Keep its ZIP contents unchanged. Do not publish the Drive folder.
The repository connector cannot upload binary data from Google Drive into GitHub directly.
The ZIP must be transferred using an authenticated GitHub upload, not a public URL.
The push of that ZIP automatically triggers `.github/workflows/visual-test-apk.yml`.
Alternatively, manually trigger the workflow after transferring the ZIP.

## Build gates
1. Verify full 5119-word dictionary and unique IDs.
2. Check every image mapping and every WebP asset before copying to Android assets.
3. Run integration validator, offline model bundling, unit tests and APK compilation.
4. Verify packaged offline speech components.
5. Publish debug artifact `EnglishWords-5119-118-Visual-Test-APK` on the workflow run.

## Device acceptance
- Samsung Galaxy S20 FE: install *alongside* existing version, offline lessons, 118 image-capable words, unrelated words without broken image placeholders; hide images before answer.
- Galaxy Fold: run on outer and inner screens; verify no cropping, important controls remain accessible, resize and orientation, and persistence of the illustrations toggle.
- On both phones: English/Russian speech, 50-word learning blocks, test answers, negative/positive results, airplane-mode behavior, interruption recovery and audio output.

No successful visual APK build or real-device test is claimed until CI artifact and phone logs exist.
