# English Words S20 FE — автономный тестовый стенд v6

Контур для физического Galaxy S20 FE 4G (SM-G780G, Android 13) под Windows 10/11.
На Windows пользователь установил ADB в `C:\\platform-tools`, GitHub CLI (`gh`) авторизован.

## Проверенный объём
Последний реальный запуск Windows Test Lab v5 (10.11.2026): PASS — главная страница, «Спроси слово», «Изучать», «Проверять» и сохранение проверки после перезапуска процесса; интернет выключался и восстанавливался. Проверка микрофона **не выполнена**; ошибка «распознано, но не засчитано» остаётся открытой.

## v6: архитектура
- Windows Task Scheduler: `EnglishWords-S20-AutoTest`, раз в 15 минут, под текущим интерактивным пользователем (без сохранения пароля), только когда он вошёл в систему.
- Локальный PowerShell 5.1-агент `EnglishWordsLab.ps1` запрашивает через `gh run list` последний успешный APK в ветке `test/0.9.7-s20fe-install`.
- Агент проверяет AndroidManifest.xml бинарного APK **до установки**, требует `com.example.english1000.s20test` и локальные Vosk RU/EN и ARM64 .so.
- Агент не является GitHub self-hosted runner и не принимает команды из GitHub. Никаких входящих портов и удалённых shell-команд.
- Один запуск полноценного теста на новый успешный workflow run и версию агента; повторных циклов установки одного и того же сбоя каждые 15 мин нет.
- Разрешена очистка **только** `com.example.english1000.s20test`. Основная `com.example.english1000` неприкосновенна.
- Полный журнал, скриншоты и XML остаются в локальной `C:\\EnglishWords-Windows-Lab\\results`.
- Краткий статус PASS/FAIL, номер CI run и фиксированный этап — [публичный issue #3](https://github.com/directdirectconnect-glitch/062/issues/3). Без ADB serial, путей Windows, XML, скриншотов, аудио, logcat, токенов.
- Существующая ежечасная автоматизация ChatGPT отслеживает новые сообщения issue #3 и CI, сообщает только о новых существенных результатах.

## Локальная установка (владелец Windows должен выполнить один раз)
Распаковать дистрибутив `EnglishWords-S20-Autolab-v6.zip` поверх `C:\\EnglishWords-Windows-Lab`, заменив `EnglishWordsLab.ps1` и `RegisterLabTask.ps1`.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\\EnglishWords-Windows-Lab\\RegisterLabTask.ps1" -AllowSandboxReset
```

Установщик создаёт задание и просит выполнить первый запуск. Запись в issue #3 после этого является подтверждением передачи результата из ПК.

## Контроль

```powershell
Get-ScheduledTask -TaskName EnglishWords-S20-AutoTest | Select TaskName, State
Get-ScheduledTaskInfo -TaskName EnglishWords-S20-AutoTest | Select LastRunTime, LastTaskResult, NextRunTime
gh issue view 3 -R directdirectconnect-glitch/062 --comments
```

Отключить: `Disable-ScheduledTask -TaskName EnglishWords-S20-AutoTest`.

## Следующие задачи после подтверждения передачи результатов
1. Premium European UI для всех экранов English Words 0.9.8.
2. Сборка той же функциональности в отдельном Android test package ID.
3. Автотест на реальном Samsung; отдельно автоматические акустические проверки RU/EN через контролируемое аудио.
4. Исправление неверного зачёта английского ответа и регрессионные тесты нормализации.
