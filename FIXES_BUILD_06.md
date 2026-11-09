# English Words 0.6 — исправление CI

GitHub Actions прогон 1: `setup-android@v3` → попытка установить устаревший SDK package `tools`, exit code 1.

Прогон 2: `gradle 8.7` + Android Gradle Plugin 8.7.3 → минимум Gradle 8.9, build fails.

Прогон 3: получено только изображение статуса failure, **без журнала**, точная причина не установлена; конфигурация целиком заменена на корректный `main.yml`.

Новый workflow использует `android-actions/setup-android@v4`, `gradle/actions/setup-gradle@v4` с Gradle 8.10.2, Java 17, SDK 35 и upload-artifact@v4.

Никакие изменения в словаре или программной логике 0.6 не производились.

Результат: готовый к загрузке исходный проект; успешная сборка APK пока НЕ подтверждена.
