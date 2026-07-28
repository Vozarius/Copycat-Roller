# Отчёт о сборке и тестах

Среда проверки:

- Windows 11 amd64
- Oracle JDK 21.0.9 LTS
- Gradle 8.14.3
- Minecraft 1.21.1
- NeoForge 21.1.219
- Create 6.0.11
- Copycats+ 3.0.4

## Итоговые команды

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer
.\gradlew.bat build
```

Финальный объединённый прогон:

```powershell
.\gradlew.bat build runGameTestServer --console=plain
```

Финальная чистая проверка версии 1.1.0:

```powershell
.\gradlew.bat clean build runGameTestServer --console=plain
```

Результат: `BUILD SUCCESSFUL in 31s`; unit-тесты прошли, dedicated
GameTest-сервер завершил `17/17` обязательных тестов за `816.9 ms`.

Артефакты:

- `build/libs/copycat_roller-1.1.0.jar` — 41 169 байт;
- `build/libs/copycat_roller-1.1.0-sources.jar` — 20 862 байта;
- SHA-256 основного JAR:
  `889D2046E01217CCAF6A00C1B09AADC20FB691582B7E2BA98C51FA7021D49167`.

## Unit-тесты

`LayerMathTest` проверяет:

- режимы `DOWN` и `UP` на значениях от `0.0` до `0.999999`;
- шум около границ 1/8;
- отрицательные мировые Y;
- переход через целую координату;
- значение `DOWN` как default для чистой математики;
- эквивалентность штатной нижней плите при `fraction=0.5`:
  `layers=8` снизу и `layers=4` сверху в режиме `UP`.

`PavingLimitsTest` проверяет default в один ближайший блок, пользовательскую
глубину и ограничение штатным `rollerFillDepth + 1`.

## NeoForge GameTests

Набор состоит из 17 обязательных тестов:

1. точный Copycat Layer принимается фильтром Roller;
2. другой неполный copycat отклоняется;
3. горизонтальный профиль не создаёт частичный верхний слой;
4. половинный уклон создаёт `layers=4` сверху;
5. состояния 1/8…7/8;
6. расход `N`;
7. расход восьми для полного блока;
8. атомарный отказ при недостатке;
9. наращивание 3 → 6 за три предмета;
10. защита пользовательского материала;
11. идемпотентный повторный проход;
12. защита незагруженного chunk;
13. защита твёрдого блока и портала;
14. совпадение X/Z-покрытия диагонали и Bezier с Create и сохранение
    неокруглённого Y;
15. строгая область compat-ветки и runtime-порядок `RollingMode`;
16. classloading общего кода на dedicated GameTest server.
17. значения конфигурации по умолчанию: `DOWN` и один ближайший блок.

## Диагностические итерации

- Первоначальная зависимость от несуществующего classifier `slim` для Maven
  build 295 Create была заменена на полный официальный artifact.
- Попытка поместить mixin в Java-пакет Create была отклонена JPMS как
  split-package; mixins перенесены в пакет аддона, ordinal режима
  централизован и защищён GameTest.
- Расширенный тест горизонтального профиля обнаружил служебную половину
  блока Create. Sampler исправлен так, чтобы offset выбора `BlockPos` не
  создавал ложные четыре слоя на ровном пути.

В server log остаются предупреждения из mixin-конфигураций Copycats+,
Flywheel и Ponder (compatibility-level, повторные `@Unique`, отсутствующий
dev refmap Ponder). Они воспроизводятся без аддона, не относятся к трём
mixin-классам Copycat Roller и не помешали применению инъекций или тестам.
