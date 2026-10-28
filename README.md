# WorkTrack

Personal daily-life system for Android (Kotlin + Jetpack Compose). Single user.
Plan every day, run your tasks, track habits, goals and money, and see how much you
actually got done.

## Features

| Area | What it does |
|---|---|
| **Today** | Score ring (done ÷ planned, Top 3 count double), streak, Top 3, a timeline mixing timed tasks with Google Calendar events, "any time" tasks, habit check-ins |
| **Plan my day** | Carry over yesterday's missed tasks, pick Top 3 (max 3), set times, see the day's Google Calendar; works for future days too |
| **Repeating tasks** | Every day / chosen weekdays / once; auto-created on each matching day; can feed a goal |
| **Reminders** | App-only reminders (one-off or daily) + next 7 days of Google Calendar (read-only) |
| **Habits** | Yes/no habits, last-7-days grid, streaks |
| **Goals** | Count goals (linked tasks add +1 when done) and money goals (savings entries) with deadlines |
| **Finance** | Income/expenses, monthly overview, category budgets (warn at 80%, red when over), recurring entries |
| **Pending inbox** | Auto-captured payments wait here — **nothing counts until you accept it** |
| **Auto-capture** | Bank SMS + GPay/PhonePe/Paytm notifications → parsed on the phone (amount, merchant, UPI ref); raw text is never stored; learns merchant → category |
| **Stats** | Today, current/best streak, 7- and 30-day bars, 18-week heatmap, habits, goals, month's money |
| **Weekly review** | Avg score, best/hardest day, habits, spend vs last week, notes; last week's "improve" shows as this week's focus |
| **Notifications** | Task alerts with **Done / Snooze**, morning "Plan your day", night review, Sunday weekly review |
| **Widget** | Today's score, streak and next task on the home screen |
| **Cloud backup** | Google Sign-In + Firestore backup/restore (daily auto backup) once Firebase is set up |
| **Backup file** | Export / import everything as a JSON file — works without Firebase |
| **Quick add & voice** | ⚡ on Today, Quick Settings tile, or an optional notification with a reply box. Understands "call mom tomorrow 6pm", "remind me to pay rent on the 5th at 9am", "someday learn guitar", "!" for high priority |
| **Task details** | Checklists (sub-steps), time estimates vs actual time, energy level (⚡ high focus / low) |
| **Auto-schedule** | Plan my day gives "any time" tasks a time: high-focus work in your peak hours, around calendar events |
| **Someday list** | Ideas without a date; schedule them when ready |
| **Focus timer** | Pomodoro with breaks; keeps running when the app is closed; time is added to the task |
| **Journal & mood** | A few lines and a 1–5 mood per day; Stats shows mood vs score |
| **Habits+** | Count habits (8 glasses, 20 pages), Health Connect auto-tick (steps, sleep, exercise), streak freezes (1 per 7 good days, max 2), "don't miss twice" warnings |
| **Location reminders** | "When I arrive at Office" — save a place where you stand, reminder fires on arrival |
| **Learning** | Books and courses with progress; finished items counted per year |
| **Bills & subscriptions** | Monthly/yearly due dates, reminder N days before, "Mark paid" records the expense, subscription cost per month |
| **Money+** | Month-end spend forecast, auto-save % of income into a money goal (via Pending), lent/borrowed, net worth with monthly trend, CSV/PDF export of a month |
| **Reviews** | Weekly, monthly and yearly reviews |
| **Life score & badges** | One 0–100 score from tasks, habits, budgets and journal; 18 badges, announced when earned |
| **More widgets** | Habit check-in widget and Top 3 widget (tap to complete) |
| **Features** | First-run setup picks what the app helps with (habits, goals, money, journal, focus, learning, medicines, dates); hidden ones keep their data. Change any time in Settings → Features |
| **Close the day** | Evening screen: starts with today's win, close open tasks (done / tomorrow / skip), tick habits and doses, mood + one line, pick tomorrow's Top 3, optional rest day tomorrow. The night notification opens it |
| **Morning brief** | Morning notification shows your Top 3, the first event or timed task, birthdays today, medicine doses and last night's sleep |
| **Rest / sick days** | Mark a day as rest: it isn't scored, and day and habit streaks pause instead of breaking |
| **Undo** | Every delete (tasks, money entries, reminders, bills, debts, assets, learning, ideas, medicines, dates) and every payment reject shows an Undo bar; rejected payments are listed under Pending with Restore |
| **Medicines** | Dose times with Taken / Snooze notifications, today's doses on Today, 7-day "taken" %, doses-left count with a refill alert |
| **Important dates** | Birthdays and anniversaries ("turns 30", "10 years"), a 9 AM heads-up N days before, shown on Today |
| **Sleep** | Optional wind-down reminder before your bedtime that opens Close the day |
| **Emergency fund** | Finance overview shows how many months of everyday spending your emergency fund covers (target 3/6/12 months); one tap creates the goal |
| **Monthly money summary** | On the 1st: last month's income, spending, what you kept, change vs the month before and the biggest category |
| **Look & privacy** | Light/dark/system theme, wallpaper colours or accent colour; app lock with fingerprint / face / screen lock |

**Google is read-only.** Calendar events (via the phone's synced calendar) and Google Tasks are
shown in the app but never changed, and they don't affect the score. Tasks and reminders you
create in the app stay in the app.

**App lock covers the app itself.** Home-screen widgets, the Quick Settings tile and notifications
(task titles, payments, medicines) still show their text while the app is locked; remove the widgets
or hide sensitive notifications in Android's lock-screen settings if that matters to you. On Android 8–12
the lock also blocks screenshots of the app (Android's only way to hide it from recent apps there).

## Data

Everything is stored on the phone (Room / SQLite, schema v3 with migrations from v1 and v2; schemas exported to `app/schemas/`) and the app works fully offline. Firebase is
optional: it adds Google Sign-In and a backup copy of the database in Firestore under
`users/{uid}`.

## Build

```bash
JAVA_HOME=/home/wac/.local/jdk/jdk-17.0.20.1+1 \
ANDROID_HOME=/home/wac/.local/android-sdk \
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`
Install: `~/.local/bin/adb install -r app/build/outputs/apk/debug/app-debug.apk`
Tests: `./gradlew :app:testDebugUnitTest` (SMS parser, quick-add parser, scores/streaks/freezes, rest days, medicines, important dates, bills, auto-schedule, checklists, money)

Stack: Gradle 9.4.1 · AGP 9.2.1 · Kotlin 2.3.21 · Compose BOM 2026.09 · Room 2.8 · compileSdk 37 · minSdk 26

## Firebase setup (turns on sign-in + backup)

1. Firebase console → create a project (use your personal Google account).
2. Add an Android app with package `com.wac.worktrack` and this SHA-1 (debug key):
   `41:BB:CC:D3:56:D0:76:A3:0F:8B:01:07:3A:E8:D1:4E:30:1C:C6:4B`
   (get it again with `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`)
3. Authentication → Sign-in method → enable **Google**.
4. Firestore Database → create (region e.g. `asia-south1`), then paste `firestore.rules` into the Rules tab.
5. Download `google-services.json` into `app/` and rebuild. (It is git-ignored.)
6. For Google Tasks: Google Cloud console → same project → APIs & Services → enable **Google Tasks API**.
   On the OAuth consent screen, set publishing status to **In production** so access doesn't expire
   every 7 days (you'll see an "unverified app" notice — tap Continue).

## Permissions (all opt-in in Settings)

- Notifications, exact alarms — task alerts and the daily routine
- Calendar (read) — show Google Calendar events
- SMS (receive/read) — bank SMS auto-capture; sideloaded only (the Play Store restricts SMS apps)
- Notification access — GPay / PhonePe / Paytm capture
- Location ("Allow all the time") — location reminders only
- Health Connect (read steps, sleep, exercise) — habit auto-tick only
- Biometric / screen lock — app lock
