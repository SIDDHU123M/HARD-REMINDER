# Hard Reminder — Implementation Instructions

## Overview

Three features to implement based on `prompt.md`. All changes are confined to the existing
Activity-based Jetpack Compose architecture (min SDK 26, Material3 1.4.0, Compose BOM 2025.02.00).

---

## Task 1 — Replace custom `ConnectedButtonRow` with M3 Expressive `ButtonGroup`

### Current state
`SettingsActivity.kt:334–392` contains a hand-rolled `ConnectedButtonRow` that simulates the
M3 Expressive Connected Button Group using overlapping `Button` / `OutlinedButton` pairs.

### What to do
Replace `ConnectedButtonRow` with the official `ButtonGroup` composable from
`androidx.compose.material3` (available as `@ExperimentalMaterial3ExpressiveApi` in 1.4.0).

**Spec reference:** https://m3.material.io/components/button-groups/specs

#### Implementation plan

1. **Delete** the entire `ConnectedButtonRow` function in `SettingsActivity.kt` (lines 333–392).

2. **Add** the opt-in annotation at the top of `SettingsActivity.kt`:
   ```kotlin
   @file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
   ```

3. **Replace every call site** with `ButtonGroup` + `ToggleButton` children.
   The M3 Expressive API shape:

   ```kotlin
   ButtonGroup(
       modifier = Modifier.fillMaxWidth()
   ) {
       items.forEachIndexed { index, label ->
           toggleButton(
               checked = index == selectedIndex,
               onCheckedChange = { if (it) onSelected(index) },
               shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()  // or middle/trailing
           ) {
               Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
           }
       }
   }
   ```

   Use `ButtonGroupDefaults.connectedLeadingButtonShapes()`,
   `ButtonGroupDefaults.connectedMiddleButtonShapes()`, and
   `ButtonGroupDefaults.connectedTrailingButtonShapes()` to assign the correct shape to each
   position (rounded-start, square, rounded-end).

4. **Usage sites in `SettingsActivity.kt`** that need updating:
   - Theme selector (System / Light / Dark) — line ~129
   - Default Prior Notification rows — lines ~183–196
   - Snooze Duration rows — lines ~211–224

5. **Usage site in `AddEditReminderActivity.kt`**: the Prior Notification card (lines ~460–488)
   uses a dropdown. Consider keeping the dropdown there for now since `ButtonGroup` is only
   required in Settings per the prompt; do NOT change `AddEditReminderActivity` unless the
   dropdown is also replaced.

#### Acceptance criteria
- No `ConnectedButtonRow` function exists anywhere in the codebase.
- Selected button renders with `primary` fill; unselected with tonal/outlined style.
- Buttons in a group share borders with no visible gap between them.
- The shape transitions are: rounded start → square middle(s) → rounded end.

---

## Task 2 — Screen transitions + Predictive Back gesture

### Current state
- Activities are started with bare `startActivity(Intent(...))` — no animation options.
- `android:enableOnBackInvokedCallback="true"` is already declared in `AndroidManifest.xml:39`,
  which opts the app into the system predictive back animation on Android 14+. No custom
  back-gesture handling is wired up yet.
- There are no `res/anim/` files.

### What to do

#### 2a — Enter / exit transitions for every `startActivity` call

Create four XML animator files under `app/src/main/res/anim/`:

| File | Description |
|---|---|
| `slide_in_right.xml` | New screen slides in from the right (enter) |
| `slide_out_left.xml` | Old screen slides out to the left (exit) |
| `slide_in_left.xml` | Previous screen slides back in from the left (pop enter) |
| `slide_out_right.xml` | Current screen slides out to the right (pop exit) |

Example `slide_in_right.xml`:
```xml
<translate xmlns:android="http://schemas.android.com/apk/res/android"
    android:fromXDelta="100%"
    android:toXDelta="0%"
    android:duration="300"
    android:interpolator="@android:interpolator/fast_out_slow_in" />
```

Wrap every `startActivity` call with `ActivityOptionsCompat`:

```kotlin
val options = ActivityOptionsCompat.makeCustomAnimation(
    this,
    R.anim.slide_in_right,   // entering activity
    R.anim.slide_out_left    // exiting activity
)
startActivity(intent, options.toBundle())
```

Calls to update:
- `MainActivity.kt:115` — open `SettingsActivity`
- `MainActivity.kt:129` — FAB opens `AddEditReminderActivity`
- `MainActivity.kt:155` — EmptyState opens `AddEditReminderActivity`
- `MainActivity.kt:188` — ReminderCard click opens `AddEditReminderActivity`
- `SettingsActivity.kt:299` — open `AboutActivity`

For the back-direction (pop) animation, override `finish()` in each child Activity and call
`overridePendingTransition` immediately after:

```kotlin
override fun finish() {
    super.finish()
    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
}
```

Apply this override in: `AddEditReminderActivity`, `SettingsActivity`, `AboutActivity`.

#### 2b — Predictive Back gesture

The system handles the predictive back swipe animation automatically because
`android:enableOnBackInvokedCallback="true"` is already set. No extra code is needed for the
default system animation.

To add a **custom** in-progress predictive back animation (optional, for a polished feel):

In each child Activity, register a `OnBackPressedCallback` via the AndroidX
`OnBackPressedDispatcher`. Use `BackEventCompat` to drive a scale/translate on the Compose
`Surface` while the user swipes:

```kotlin
onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        // Drive a Compose state variable to shift/scale the surface
        backProgress = backEvent.progress
    }

    override fun handleOnBackCancelled() {
        backProgress = 0f
    }
})
```

Expose `backProgress` as a `mutableFloatStateOf` and apply it to the root `Scaffold` container
via `graphicsLayer { scaleX = 1f - backProgress * 0.05f; scaleY = 1f - backProgress * 0.05f }`.

#### Acceptance criteria
- Navigating forward (MainActivity → Settings, MainActivity → AddEdit) shows a right-slide transition.
- Pressing back or swiping back shows a left-slide transition.
- On Android 14+ devices, the system predictive back peek animation is visible during a slow back swipe.

---

## Task 3 — Replace custom `SettingSwitchRow` with M3 Expressive `ListItem`

### Current state
`SettingsActivity.kt:394–410` defines `SettingSwitchRow`, a custom `Row` that mimics a list
item. All settings toggle rows use this composable. It does not conform to the M3 list item
spec.

**Spec reference:** https://m3.material.io/components/lists/specs

### What to do

1. **Delete** the `SettingSwitchRow` function (lines 394–410).

2. **Replace every call site** with the official `ListItem` composable:

   ```kotlin
   ListItem(
       headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
       supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodyMedium) },
       trailingContent = {
           Switch(checked = checked, onCheckedChange = onCheckedChange)
       },
       modifier = Modifier.clickable { onCheckedChange(!checked) }
   )
   ```

3. **Settings call sites to update** (all in `SettingsActivity.kt`):
   - AMOLED Dark Mode — line ~143
   - Material You — line ~149
   - Default Sound — line ~167
   - Default Vibration — line ~169
   - Auto-delete Fired Reminders — line ~228
   - Use 24-hour Format — line ~230
   - Alarm Wake Screen — line ~272
   - Start on Boot — line ~274
   - Ongoing Notification — line ~276

4. **Battery Optimization row** (lines ~246–269) is already a custom `Row` with no switch.
   Replace it with a `ListItem` too:

   ```kotlin
   ListItem(
       headlineContent = { Text("Battery Optimization") },
       supportingContent = {
           Text(
               text = if (batteryExempt) "Unrestricted" else "Restricted — tap to fix",
               color = if (batteryExempt) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.error
           )
       },
       trailingContent = {
           if (!batteryExempt) {
               Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
           }
       },
       modifier = Modifier.clickable { /* open battery settings */ }
   )
   ```

5. **"About Hard Reminder" card** (lines ~295–318) can also be simplified to a `ListItem`
   inside the existing `Card`:

   ```kotlin
   ListItem(
       headlineContent = { Text("About Hard Reminder") },
       leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
       trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
       modifier = Modifier.clickable { startActivity(Intent(this@SettingsActivity, AboutActivity::class.java)) }
   )
   ```

6. **Remove the outer `Card` wrappers** that previously grouped `SettingSwitchRow` items if
   the `ListItem` composable provides sufficient visual grouping via its own surface/container
   colors. Keep the section header `Text` composables (Appearance, Defaults & Automation, etc.)
   as-is — they serve as list subheaders.

   If visual grouping cards are still desired, keep one `Card` per section but remove the
   inner `Modifier.padding(horizontal = 16.dp, vertical = 8.dp)` Column wrapper since
   `ListItem` manages its own padding per spec (72dp tall for two-line items).

#### Acceptance criteria
- No `SettingSwitchRow` function exists in the codebase.
- Every toggle row in Settings is rendered via `ListItem` with `headlineContent`,
  `supportingContent`, and `trailingContent` slots.
- List item touch targets meet the M3 spec (minimum 56dp height for one-line, 72dp for two-line).
- Dividers between items are either removed (M3 Expressive lists prefer no dividers) or replaced
  with `HorizontalDivider` at the section boundary only.

---

## File change summary

| File | Changes |
|---|---|
| `app/src/main/java/com/hardreminder/ui/SettingsActivity.kt` | Delete `ConnectedButtonRow` + `SettingSwitchRow`; replace all call sites with `ButtonGroup` and `ListItem`; add `@file:OptIn` annotation |
| `app/src/main/java/com/hardreminder/ui/MainActivity.kt` | Wrap all `startActivity` calls with `ActivityOptionsCompat.makeCustomAnimation` |
| `app/src/main/java/com/hardreminder/ui/AddEditReminderActivity.kt` | Override `finish()` for pop transition; add predictive back callback |
| `app/src/main/java/com/hardreminder/ui/AboutActivity.kt` | Override `finish()` for pop transition; add predictive back callback |
| `app/src/main/res/anim/slide_in_right.xml` | New file |
| `app/src/main/res/anim/slide_out_left.xml` | New file |
| `app/src/main/res/anim/slide_in_left.xml` | New file |
| `app/src/main/res/anim/slide_out_right.xml` | New file |

No changes are needed to `build.gradle.kts`, `AndroidManifest.xml`, Room entities,
AlarmScheduler, or any receiver/service classes.
