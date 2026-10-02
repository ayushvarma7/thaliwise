# ThaliWise and Health Connect: what we use, what exists, what we could build

ThaliWise uses the Health Connect API built into Android (`android.health.connect`, Android 14 and newer). It needs no extra library and makes no network calls. The counts below come from the Android 15 SDK (API 35) that ThaliWise compiles against, read directly from its `android.jar`.

## 1. What ThaliWise uses today

| Data | Record | How | Shown in |
|---|---|---|---|
| Steps today | `StepsRecord` | aggregate `STEPS_COUNT_TOTAL` from local midnight | Today steps bar, coach walk tips |
| Calories burned today | `TotalCaloriesBurnedRecord` | aggregate `ENERGY_TOTAL` | Today "Burned" |
| Active calories today | `ActiveCaloriesBurnedRecord` | aggregate `ACTIVE_CALORIES_TOTAL` | Today "Burned (active)" |
| Calories eaten today | `NutritionRecord` | aggregate `ENERGY_TOTAL` | Today calorie ring, coach budget tips |
| Meals (every app) | `NutritionRecord` | `readRecords`, today and the last 7 days | Today meal list, Diary |
| Logging a meal | `NutritionRecord` | `insertRecords`: energy, protein, total carbohydrate, total fat, meal name, meal type | after "Log" on the result screen |
| Undo or delete | `NutritionRecord` | `deleteRecords`, only records this app wrote | Undo, Diary delete |

Permissions requested: 5 (`READ_STEPS`, `READ_ACTIVE_CALORIES_BURNED`, `READ_TOTAL_CALORIES_BURNED`, `READ_NUTRITION`, `WRITE_NUTRITION`). API calls used: 4 of 9 (`aggregate`, `readRecords`, `insertRecords`, `deleteRecords`).

## 2. What Health Connect offers

### The numbers

| | Available (API 35) | ThaliWise uses |
|---|---|---|
| Record types | **40** | 4 read, 1 written |
| Permissions | **78** (38 read and 38 write data permissions, the exercise-route pair, background reads, history reads) | 5 |
| Nutrient fields per meal record | **42** | 4 written |
| Ready-made aggregate metrics | **87** (totals, averages, minimums, maximums across 22 record types) | 4 |
| Manager calls | **9** | 4 |

Every one of the 40 record types can be **read** with its `READ_` permission and **written** with its `WRITE_` permission. Health Connect shows the user one switch per permission.

### All 40 record types

| Category | Record types |
|---|---|
| Activity (14) | Steps, Steps cadence, Distance, Active calories burned, Total calories burned, Exercise session (with segments, laps, and an optional route), Planned exercise session, Floors climbed, Elevation gained, Speed, Power, Cycling pedaling cadence, Wheelchair pushes, VO2 max |
| Body (7) | Weight, Height, Body fat, Lean body mass, Bone mass, Body water mass, Basal metabolic rate |
| Vitals (9) | Heart rate, Resting heart rate, Heart rate variability (RMSSD), Blood pressure, Blood glucose, Oxygen saturation, Respiratory rate, Body temperature, Skin temperature |
| Sleep (1) | Sleep session (with stages: awake, light, deep, REM, and more) |
| Nutrition (2) | Nutrition (meals), Hydration |
| Cycle tracking (7) | Menstruation flow, Menstruation period, Ovulation test, Cervical mucus, Intermenstrual bleeding, Basal body temperature, Sexual activity |

Skin temperature and planned exercise sessions arrived through a later SDK extension, so a phone needs a recent Health Connect module for them. Newer Android SDKs add more (mindfulness sessions, activity intensity, and a separate FHIR-based medical records API). Using those means raising `compileSdk` beyond 35, so they are not counted here.

### The 42 nutrient fields of a meal record

| Group | Fields |
|---|---|
| Energy (2) | energy, energy from fat |
| Macronutrients (11) | protein, total carbohydrate, sugar, dietary fiber, total fat, saturated fat, unsaturated fat, monounsaturated fat, polyunsaturated fat, trans fat, cholesterol |
| Minerals (14) | sodium, potassium, calcium, iron, magnesium, zinc, phosphorus, selenium, copper, manganese, chromium, molybdenum, iodine, chloride |
| Vitamins (14) | vitamin A, B6, B12, C, D, E, K, thiamin, riboflavin, niacin, folate, folic acid, biotin, pantothenic acid |
| Other (1) | caffeine |

A meal record also has a meal name and a meal type (breakfast, lunch, dinner, snack). Each of the 42 fields has its own aggregate (for example total protein or total sodium for a day), so daily nutrient totals cost one call.

### Ways to read

| Call | Gives | Good for |
|---|---|---|
| `readRecords` | the raw records in a time range, filterable by the app that wrote them | meal lists, detailed history |
| `aggregate` | one total, average, minimum, or maximum over a range, already deduplicated across apps | "today" numbers |
| `aggregateGroupByDuration` | the same, in fixed buckets (for example every hour) | intraday charts, meal-time patterns |
| `aggregateGroupByPeriod` | the same, by calendar day, week, or month | weekly and monthly trends |
| `getChangeLogToken` and `getChangeLogs` | what changed since last time | refreshing only when something changed (saves battery) |
| `updateRecords` | edit a record this app wrote | correcting a logged meal instead of delete and re-add |

Special permissions: `READ_HEALTH_DATA_IN_BACKGROUND` (read while the app is closed, needed for reminders) and `READ_HEALTH_DATA_HISTORY` (read data older than 30 days).

## 3. Ideas for ThaliWise

Ranked by value for effort. Each says which data it needs and whether that means a new permission. **Tier 1** uses data ThaliWise already reads, or data that is not sensitive. **Tier 3** is health-sensitive and should be strictly opt-in.

### Tier 1: small changes, clear value

| # | Idea | Data | New permission |
|---|---|---|---|
| 1 | **Personal walk math.** Learn the user's own kcal per 1,000 steps from the last 14 days of steps and active calories (`aggregateGroupByPeriod`), and use it instead of the generic 3.5 MET estimate in walk tips. | Steps, Active calories | none |
| 2 | **Earned calories.** Raise today's budget by the active calories above the user's usual day, so a long walk shows up as room to eat, or keep the budget fixed. Make it a setting. | Active or Total calories, optional Basal metabolic rate | `READ_BASAL_METABOLIC_RATE` (optional) |
| 3 | **Fuller meal records.** Write sugar, fiber, sodium, saturated fat, and caffeine with every meal, which needs those columns in `foods.txt`. Other apps (Fitbit, Google Fit) then see them too. | Nutrition | none |
| 4 | **Weekly and monthly trends** in the Diary: eaten against burned, steps, average meal time, days on budget. | Nutrition, Steps, Total calories | none (`READ_HEALTH_DATA_HISTORY` for more than 30 days) |
| 5 | **Weight follows the scale.** Read weight from a smart scale or Fitbit, offer to update the profile, and recompute the budget. Write the weight the user types in onboarding. | Weight, Height | `READ_WEIGHT`, `WRITE_WEIGHT`, `READ_HEIGHT` |
| 6 | **Water.** A "+1 glass" button on Today writes hydration, plus a coach line. | Hydration | `READ_HYDRATION`, `WRITE_HYDRATION` |
| 7 | **No double counting.** Show which app logged each meal, and warn when the same meal looks logged twice (same time window, similar calories, different apps). | Nutrition (data origin) | none |
| 8 | **Refresh only on change.** Use change logs so Today reads Health Connect only when something new arrived. | all current types | none |

### Tier 2: bigger features

| # | Idea | Data | New permission |
|---|---|---|---|
| 9 | **Workout-aware coach.** After a workout today, suggest a protein-rich meal and count the workout's active calories. Fits the "Build muscle" goal. | Exercise session, Active calories | `READ_EXERCISE` |
| 10 | **Sleep-aware tips.** After a short night, a gentle, factual note (for example that short sleep often comes with stronger cravings), never a rule. | Sleep session | `READ_SLEEP` |
| 11 | **Caffeine and sleep.** Caffeine from logged drinks (coffee, chai, energy drinks) next to bedtime: "most of today's caffeine was after 4 PM". | Nutrition (caffeine), Sleep | `READ_SLEEP` |
| 12 | **Evening reminder.** At a time the user picks: "3,000 steps to go, about a 30 minute walk". Needs notifications. | Steps | `READ_HEALTH_DATA_IN_BACKGROUND`, `POST_NOTIFICATIONS` |
| 13 | **Fitness trend.** Resting heart rate and VO2 max over weeks as positive feedback for a walking habit. | Resting heart rate, VO2 max | `READ_RESTING_HEART_RATE`, `READ_VO2_MAX` |
| 14 | **Meal timing.** Late-night eating and long gaps from meal times, shown as a pattern, not a judgment. | Nutrition | none |

### Tier 3: health-sensitive, strictly opt-in, local only

| # | Idea | Data | New permission |
|---|---|---|---|
| 15 | **Glucose response by dish.** For users whose glucose meter or CGM app writes to Health Connect: the rise in the 2 hours after each logged meal, and which dishes raise it most. Information only, never advice. | Blood glucose, Nutrition | `READ_BLOOD_GLUCOSE` |
| 16 | **Sodium and blood pressure.** Daily sodium from logged meals next to blood pressure readings. | Nutrition (sodium), Blood pressure | `READ_BLOOD_PRESSURE` |
| 17 | **Cycle context.** Explain normal appetite and weight changes across the cycle, so the coach does not misread them. | Menstruation period | `READ_MENSTRUATION` |
| 18 | **Allergies from medical records** (newer Android only). Turn recorded allergies into automatic diet warnings. | Medical records (FHIR) | medical-data permissions, higher compileSdk |

## 4. Rules for every new data type

1. Declare the permission in the manifest and ask for it only when the feature is first used, with a one-line reason.
2. Update the privacy text (`privacy_policy_text`, shown by Health Connect) and README section 11 in the same change.
3. Write only what the user confirmed. Never write sample or test records to a real phone. Delete only records this app wrote.
4. Keep everything on the phone. Health data never goes into the experiment log beyond the numbers a feature shows.
5. Tier 3 features are off by default, explain what they read, and state that they are not medical advice.
6. Publishing on Google Play requires declaring every Health Connect data type and its use in the Play Console.
