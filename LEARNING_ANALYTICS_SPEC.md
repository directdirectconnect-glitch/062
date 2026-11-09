# English Words — интеллектуальный анализ забывания и персональная практика
Статус: техническое задание (не реализация). Планируемый этап: 0.8.8+.
База: develop/0.8.7-ui. Функции полностью автономные, без облака.

## 1. Продуктовая задача
Находить устойчивые затруднения в изучении английских слов, выявлять возможные связи между ошибками, рекомендовать тип тренировки и проверять, помогает ли он пользователю. Не ставить диагнозов и не утверждать причинность на основе одной ошибки. Сохранять контроль пользователя над учебным процессом и скоростью перехода к следующему слову (по умолчанию 4 с, альтернативы 3/5/7 с либо вручную).

## 2. Факт о текущей кодовой базе
На момент составления документа прогресс хранится в SharedPreferences progress_v1 в виде последних значений prefs["${direction}_${wordId}"] = 1/0, обновляемых методом grade(). Отдельного хронологического журнала попыток, времени ответа, причин ошибки и признаков подсказок нет. Поэтому исторические паттерны пока не могут быть обоснованно восстановлены.

## 3. Журнал попыток (локальная база Room/SQLite)
Событие WordAttempt:
- event_id UUID (идемпотентность записи при повороте/складывании Fold);
- session_id, block_id, word_id, timestamp_utc, displayed_direction (en_ru/ru_en);
- answer_mode (manual, speech_on_device), result (correct, incorrect, skipped, unavailable);
- graded_by (user, recognizer), recognizer_confidence_nullable, recognition_error_code_nullable;
- response_latency_ms_nullable: отсчитывается только после завершения вопроса/озвучивания; при паузах, открытии меню, диалога примеров, смене конфигурации = null либо явно учтённое активное время;
- examples_opened_before_answer (boolean), hint_used (boolean), repeat_audio_count, examples_played_count;
- word_revision and app_schema_version to avoid mixing incompatible records.

Important: recognized text should not be retained by default; original voice/audio recordings are NEVER retained. No unrequested uploads, all analysis on device. Optional export/reset. Current last-result preferences can be migrated as baseline *without fabricated historical timestamps*.

## 4. Semantic tags and candidate explanations
Each word can have curated tags independent of statistical inference:
- thematic contexts: travel, restaurants, hotel, transport, shop, work, everyday life;
- part of speech: noun, verb, adjective, function word;
- irregular verb, article, phrasal verb, many meanings, homophone, visually similar spelling;
- pronunciation pattern (e.g. /th/, silent letters), optional spelling family, word family;
- difficulty band A1–B1; semantic-similarity group ID (hand-curated, never guessed from substring alone).

Algorithms only suggest candidate mechanisms:
- en_ru significantly easier than ru_en -> passive recognition stronger than active recall;
- repeated mix-ups in a curated semantic group -> train contrastive pairs;
- repeated mistakes with forms of irregular verbs -> isolate form training;
- answer correct immediately after an example but wrong in a later hint-free question -> possible reliance on hints/context;
- errors after longer time gaps -> adjust review schedule;
- recognizer-only failures without manual confirmation -> technology uncertainty, **not** proof learner is wrong.

One isolated failure never produces a "you cannot remember X" conclusion.

## 5. Evidence thresholds
- Display "needs review" after one actual incorrect/skipped attempt; not an explanation.
- At least 3 valid attempts for an individual-word pattern, spanning at least 2 distinct sessions before offering a word-specific hypothesis.
- At least 5 valid observations across 3+ words in a category before suggesting a category-level issue.
- Never use unconfirmed speech-recognition errors for personalized diagnosis. Present confidence labels "наблюдается", "возможно", "недостаточно данных".
- Avoid discriminative assumptions about age, cognition, intelligence or health.

## 6. Recommendations and spaced repetition
Create a WordReviewState for each word+direction:
- ease/stability estimate, consecutive_correct, last_review_at, next_due_at, recent_error_streak, last_recommendation.
- Initial conservative schedule after a clean, hint-free success: +1 day, +3 days, +7 days, +14 days, then adaptive extension; an error shortens the interval. Exact intervals are tuneable, not universal guarantees.
- A word failed within a block can reappear after at least 3 different intervening words (not immediately) and should not increase the 50 *new words* count.
- Do not let an analytics module auto-force the next lesson: the learner decides when to continue after a 50-word block. Support a daily target of up to 500 new words with extra review queue separately accounted.
- Do not overconcentrate on difficult words: mix due reviews, new words, and successful items; limit repeated failures in a session.

Recommended modes:
A. "Различаем похожие слова": choose between near synonyms or related words, add 2-3 contrast examples.
B. "Активное вспоминание": Russian prompt -> English without hint, optional speech or manual fallback.
C. "Понимание на слух": play offline English phrase, show answer only when requested.
D. "Формы глагола": select or say irregular forms in 1-2 natural sentences.
E. "Повторить по расписанию": lightweight review queue.

## 7. Example UI
In the lesson's ⋮ menu add "Анализ обучения" / "Learning insights". No diagnostic banners blocking the active word. 
Screen sections:
- Трудные слова: due and persistent, with evidence count and chosen direction;
- Наблюдаемые закономерности: hypothesis + supporting attempts + small caveat;
- Рекомендуем сегодня: three to ten minutes, opt-in; 
- Упражнение помогло?: compare word success rates after repeated sessions.

Example explanation:
"В трёх занятиях слово 'take' правильно узнавалось с английского, но дважды не вспоминалось с русского. Попробуй 3 коротких фразы в направлении RU→EN. Это предположение по истории ответов."
If evidence weak: "Недостаточно данных; продолжим обычные блоки."

## 8. Settings and privacy
- Analytics enabled on device by default, but users can disable new tracking and erase history without resetting known/unknown word state if they choose.
- Never transmit word performance, recognized speech or telemetry to external servers.
- Allow local backup/export in future (JSON/CSV) after explicit user action.
- Data persists through app restarts, screen folding, locale/font/theme changes. Migration of prefs is non-destructive.

## 9. Implementation phases
Phase A (0.8.8): event schema + tests + local repository + data migrations, no diagnostic claims.
Phase B (0.8.9): per-word history, review scheduler, due-word filters, deterministic rule engine.
Phase C (0.9.x): manual semantic tags, group analysis, explainable recommendations, screen.
Phase D: evaluation over repeated sessions, falsification of weak hypotheses, optional A/B heuristics.
Each phase builds separately, with Galaxy Fold and conventional phone layouts.

## 10. Acceptance criteria
1. A wrong attempt with word 72/go in RU→EN is stored once; folding the device never duplicates it.
2. An on-device ASR error is marked unavailable and does not count as a learner error.
3. Changing theme, font or interface language does not clear history or trigger the timer.
4. Read/listen to 6 phrase examples before/after answering; no spontaneous move while examples are open.
5. Default auto transition stays 4s; options 3/5/7/manual preserved; opening analytics/settings pauses the timer.
6. A single failed answer creates only an "needs review" status, not a speculative reason.
7. A stable 3-attempt cross-session pattern may produce a cautious hypothesis with evidence attached.
8. Review queue works offline and preserves the user's 50-word block accounting, up to 500 new words/day.
9. Repeated "Знаю/Не знаю" self-report never masquerades as objective phonetic accuracy.
10. Export/deletion/disable controls operate without network and do not delete a running lesson by accident.

## 11. Out of scope until actual device testing
- Psychological profiling, diagnosing memory problems, claiming causality.
- Speech-accent scoring or saving personal voice recordings.
- Personalized ML model training on users' private recordings.
- Cloud analytics, public rankings, medical or cognitive tests.
