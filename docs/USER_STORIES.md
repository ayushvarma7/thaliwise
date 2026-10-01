# IdentifyVLM: product, user profiling, user stories, and UI storyboard

## 1. Product in one paragraph

IdentifyVLM is a private, offline food and activity companion for Android. You snap a meal; an on-device vision model names the dish and its cuisine; the app finds the calories for a typical serving, lets you confirm the portion, and logs the meal into Health Connect next to the steps and calories burned that Fitbit or Google Fit already record. The home screen answers one question at a glance: "How am I doing today?" Nothing leaves the phone except the one-time model download.

## 2. Personas

| Persona | Who | Why they open the app | What would make them stop |
|---|---|---|---|
| **Ayush (primary)** | Graduate student in Boston. Eats out often (fast food, Indian, Mexican, Chinese takeout) and cooks Indian food at home. Has a Pixel 8 with Fitbit and Google Fit. | Wants to see what a meal costs in calories compared with how much he walked, without typing food names into a search box. | Slow answers, wrong dishes for Indian food, having to re-enter the same correction again and again. |
| **Maya** | Nurse working 12-hour shifts, vegetarian, wants to lose 5 kg. | A quick log between shifts, a calorie budget that fits her body, and a clear "kcal left" number. | Forms that ask for too much, meat dishes suggested for her vegetarian food, guilt-heavy messages. |
| **Diego** | College athlete training for soccer, Mexican and American food. | Making sure he eats enough on training days (gain or maintain), tracking protein. | A budget that assumes weight loss, no way to edit calories for big portions. |

## 3. User profiling (onboarding)

Onboarding runs once on first launch (and again from Settings > Edit profile). Every question can be skipped; the app then falls back to safe defaults. Answers are stored only on the phone (SharedPreferences) and are written to the local experiment log as a `profile_saved` event.

| Step | Questions | Why the app asks | How the answer is used now | Used later |
|---|---|---|---|---|
| 1. Welcome | Your name | A personal greeting | "Hi Ayush" on Today | Nudge messages ("Ayush, ...") |
| 2. About you | Age, sex (female, male, prefer not to say), height, weight (US or metric units), activity level (mostly sitting, lightly active, moderately active, very active) | A calorie budget needs body size and activity | Daily calorie budget (Mifflin-St Jeor resting energy x activity factor) and a step goal by activity level | Portion suggestions |
| 3. Why you are here | Main goal (lose weight, maintain, build muscle, eat healthier, just track); reasons (understand what I eat, eat out a lot, cook more at home, doctor recommended, training for a sport, curious about calories) | The goal changes the budget and the tone | Lose: budget minus 500 kcal (never below 1,200 or 1,500 for men); build muscle: plus 300 | Which nudges to show and how often |
| 4. Food you love | Favorite cuisines (Indian, Mexican, Chinese, Japanese, Korean, Thai, Vietnamese, Italian, Mediterranean, Middle Eastern, American, Southern and BBQ, Caribbean, Latin American, Fast food) | The model sometimes hesitates between dishes; the cuisines you eat most are the better guess | A small ranking boost for table rows of your favorite cuisines when a label matches several rows | Meal ideas |
| 5. How you eat | Diet (vegetarian, vegan, pescatarian, halal, kosher, gluten-free, dairy-free, no restrictions); what you want to eat more of (more protein, more vegetables, less sugar, fewer fried foods, smaller portions, more home cooking) | Respecting restrictions and goals | Shown in Settings as part of the profile | Warnings when a dish conflicts with the diet, and nudges toward "eat more of" |
| 6. Your plan | The computed budget and step goal, both editable; optional Connect Health Connect | The user should see and own the numbers | Saved as the daily budget and step goal | |

## 4. Epics and user stories

Format: As a ..., I want ..., so that .... Acceptance criteria (AC) are testable on the phone.

### E1. Onboarding and profile

- **US-1.1** As a new user, I want a short welcome that tells me what the app does and that my data stays on my phone, so that I trust it before I answer questions.
  AC: first launch opens onboarding, not the main screens; the welcome mentions on-device processing; a progress bar shows step 1 of 6.
- **US-1.2** As a new user, I want to enter age, sex, height, weight, and activity in the units I use, so that the budget fits me.
  AC: a US/Metric toggle switches between ft/in + lb and cm + kg; values are validated (age 13 to 100, height 120 to 230 cm, weight 30 to 300 kg); leaving them empty is allowed.
- **US-1.3** As a new user, I want to say why I am here, so that the app sets the right budget and tone.
  AC: exactly one goal; any number of reasons; Back keeps earlier answers.
- **US-1.4** As a new user, I want to pick the cuisines I eat, so that identification favors my food.
  AC: multi-select chips; the choice is saved and visible in Settings.
- **US-1.5** As a new user, I want to record my diet and what I want to eat more of, so that the app respects them.
  AC: multi-select chips; "No restrictions" clears the other diet chips.
- **US-1.6** As a new user, I want to see my daily calorie budget and step goal with a one-line explanation and change them, so that I own the plan.
  AC: the plan shows the computed values; edits are kept; Start saves everything and opens Today.
- **US-1.7** As a returning user, I want to edit my profile later, so that the plan follows changes in my life.
  AC: Settings > Edit profile reopens onboarding with my answers filled in.

### E2. Snap and identify

- **US-2.1** As a user, I want one big "Snap meal" button on the home screen, so that logging starts in one tap.
  AC: Snap meal opens the capture screen with Take photo and Pick from gallery.
- **US-2.2** As a user, I want the dish name and its cuisine, so that I know what the app thinks I am eating.
  AC: the result shows the dish name and a cuisine chip; Indian, Mexican, Chinese, Japanese, Korean, Thai, Vietnamese, Italian, Mediterranean, Middle Eastern, American regional, Caribbean, and Latin American dishes and common US chains are in the table (about 300 rows).
- **US-2.3** As a user, when the photo is not food or the dish is not in the table, I want to name it myself, so that I can still log it.
  AC: "No food recognized" card with Name the food (search the table) and Retake.
- **US-2.4** As a user, I want identification to work offline, so that it works anywhere.
  AC: with airplane mode on, identification and the calorie lookup still work.

### E3. Calories and logging

- **US-3.1** As a user, I want the calories for one serving shown big, so that I see the cost of the meal at once.
  AC: kcal card with "450 kcal per sandwich" and the matched food name.
- **US-3.2** As a user, I want to set the portion and correct the calories, so that the log matches what I ate.
  AC: portion 0.5x to 3x; editable kcal (1 to 5,000); values are marked as estimates with their source.
- **US-3.3** As a user, I want Log to save the meal into Health Connect, so that it sits next to my activity data.
  AC: one NutritionRecord with name, kcal, macros, and meal type from the time of day; the card then shows "Logged ..." and today's totals.
- **US-3.4** As a user, I want Undo right after logging, so that a mistake costs nothing.
  AC: Undo deletes the record; the card returns to the unlogged state.
- **US-3.5** As a user, I want the app to learn my corrections, so that it does not repeat a mistake.
  AC: logging a different food than the model's guess saves a correction to the local memory; logging the guessed food saves an acceptance.

### E4. Today dashboard

- **US-4.1** As a user, I want a calorie ring with eaten versus my budget and the kcal left, so that I know where I stand.
  AC: the ring fills with eaten / budget; the text shows "kcal left" or "kcal over budget" (over shown in a warning color).
- **US-4.2** As a user, I want my steps against my goal, so that I see whether I moved enough.
  AC: a progress bar with "4,120 / 10,000" and "5,880 steps to go" or "Step goal reached".
- **US-4.3** As a user, I want calories burned (total and active), so that I can compare intake with output.
- **US-4.4** As a user, I want today's meals listed with time and kcal, so that I can review the day.
  AC: meals from every app in Health Connect, newest first; meals from other apps are marked.
- **US-4.5** As a user without Health Connect access, I want a clear Connect prompt, so that the dashboard is not silently empty.

### E5. Diary

- **US-5.1** As a user, I want the last 7 days of meals grouped by day with a daily total, so that I see patterns.
- **US-5.2** As a user, I want to delete a meal this app logged, so that the diary stays correct.
  AC: tapping a meal logged by this app asks to delete it; meals from other apps explain that they are deleted in Health Connect.

### E6. Privacy and trust

- **US-6.1** As a user, I want all identification and profiling on the phone, so that my body data and meals stay private.
  AC: the only network use is the model download; the profile is stored locally; Clear history and memory deletes history, photos, embeddings, and the experiment log.

### E7. Experiment tracking (developer)

- **US-7.1** As the developer, I want every run, profile save, and meal logged as JSON lines, so that I can tune the threshold, portions, and prompts later.
  AC: `run`, `feedback`, `meal_logged`, `meal_undone`, `profile_saved` events in the experiment log.

### E8. Later (not in Phase 10)

- **US-8.1** Nudges: "Ayush, you are at 3,200 of 10,000 steps and this double cheeseburger is about 450 kcal. A 25 minute walk would burn about 150 kcal."
- **US-8.2** Diet warnings: a vegetarian user is warned when a dish contains meat.

## 5. UI storyboard

```
 FIRST LAUNCH                                                     EVERY DAY
 +-------------------+ +-------------------+ +-------------------+   +----------------------+
 | 1 Welcome         | | 2 About you       | | 3 Why you are here|   | Today         (home) |
 | Snap meals, see   | | Age  [28]         | | (o) Lose weight   |   | Hi Ayush             |
 | calories, keep up | | Sex  [F][M][-]    | | ( ) Maintain      |   | Wednesday, Oct 1     |
 | with steps. All   | | Units [US][Metric]| | ( ) Build muscle  |   |  .----.  1,240       |
 | on your phone.    | | Height [5]ft[10]in| | ( ) Eat healthier |   | ( ring ) of 2,250    |
 | Name [Ayush    ]  | | Weight [170] lb   | | ( ) Just track    |   |  '----'  1,010 left  |
 |                   | | Activity: (o) ... | | [eat out] [cook]  |   | Burned 1,560 (320)   |
 |        [Next]     | |   [Back] [Next]   | |   [Back] [Next]   |   | Steps ====--  4,120  |
 +-------------------+ +-------------------+ +-------------------+   |  of 10,000           |
 +-------------------+ +-------------------+ +-------------------+   | Meals today          |
 | 4 Food you love   | | 5 How you eat     | | 6 Your plan       |   | 12:40 Tikka masala   |
 | [Indian] [Mexican]| | [Vegetarian] [..] | | 2,250 kcal a day  |   |        400 kcal      |
 | [Chinese][Thai].. | | Eat more of:      | | 10,000 steps      |   |       [ Snap meal ]  |
 |                   | | [Protein][Veggies]| | [Connect Health]  |   +----------------------+
 |   [Back] [Next]   | |   [Back] [Next]   | |   [Back] [Start]  |   | Today | Diary | Set. |
 +-------------------+ +-------------------+ +-------------------+   +----------------------+

 SNAP                   RESULT (food)            LOG MEAL DIALOG          RESULT (logged)
 +------------------+   +------------------+     +------------------+     +------------------+
 | [  photo area  ] |   | [ photo        ] |     | Food [tikka    ] |     | [ photo        ] |
 |                  |   | Chicken tikka    |     | (o) Chicken tikka|     | Chicken tikka    |
 | [Take photo]     |   |  masala [Indian] |     |     masala 400   |     |  masala [Indian] |
 | [From gallery]   |   | Creamy tomato... |     | ( ) Butter chick.|     | Logged 400 kcal  |
 |                  |   | +--------------+ |     | Portion 1x [===] |     | as lunch         |
 |   [Identify food]|   | | 400 kcal     | |     | kcal [400]       |     | Today: 1,640     |
 +------------------+   | | per cup      | |     | lunch, estimate  |     | eaten, 1,560 ... |
                        | | [Log meal]   | |     |  [Cancel] [Log]  |     | [Done] [Undo]    |
                        | | [Different]  | |     +------------------+     +------------------+
                        | +--------------+ |
                        +------------------+

 RESULT (not food)      DIARY                    SETTINGS
 +------------------+   +------------------+     +----------------------+
 | No food          |   | Today  1,640 kcal|     | Profile              |
 | recognized. The  |   | 12:40 Tikka  400 |     | Ayush, 28, lose wt   |
 | model saw: mouse |   | Yesterday 2,100  |     | 2,250 kcal, 10k step |
 | [Name the food]  |   | 20:10 Pho    500 |     | [Edit profile]       |
 | [Retake]         |   | ...              |     | Health Connect, Model|
 +------------------+   +------------------+     | Memory, Performance, |
                                                 | Telemetry, History   |
                                                 +----------------------+
```

## 6. Out of scope for Phase 10

Notifications, background checks, diet warnings, barcode scanning, recipes, restaurant menus beyond the table, cloud sync, accounts.
