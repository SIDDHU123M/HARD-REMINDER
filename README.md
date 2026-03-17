# Android Copilot Config — Setup Guide

A complete GitHub Copilot configuration package for professional Android UI/UX
with Jetpack Compose and Material Design 3.

---

## File Structure

```
.github/
├── copilot-instructions.md              ← Read on EVERY Copilot request (global rules)
├── instructions/
│   └── android-ui.instructions.md      ← Applied to all *.kt files (patterns & templates)
├── skills/
│   └── compose-ui/
│       └── SKILL.md                    ← On-demand UI generation skill
└── agents/
    └── android-ui-designer.agent.md    ← Custom agent mode for UI design tasks
```

---

## Setup Instructions

### 1. Copy to your Android project root
```bash
cp -r .github/ /path/to/your/android/project/
```

### 2. Restart GitHub Copilot in Android Studio
In Android Studio: File → Invalidate Caches → Restart

### 3. Verify Copilot picks up instructions
Open Copilot Chat, type: `@workspace What instructions are you following?`
It should describe your Android design rules.

---

## How to Use Each File

### `copilot-instructions.md`
Automatically injected on every Copilot Chat or Agent request.
Contains: tech stack, architecture rules, UI token rules, spacing system, what NOT to do.
No action needed — it just works.

### `android-ui.instructions.md`
Automatically applied when Copilot is working on `*.kt` files.
Contains: screen/ViewModel patterns, card templates, state patterns, LazyList rules, Theme template.

### `compose-ui/SKILL.md`
Invoke explicitly in Copilot Chat:
```
Read .github/skills/compose-ui/SKILL.md then generate a product listing screen.
```
Contains: full M3 color/typography role reference tables, component selection guide,
animation patterns, adaptive layout rules, accessibility checklist.

### `android-ui-designer.agent.md`
Activates in Copilot Agent Mode. Select it from the agents list in VS Code Copilot Chat.
Gives Copilot a full UI designer persona: it reads context, states design intent,
generates complete screens with previews, and self-audits before responding.

**Audit an existing screen:**
```
Review HomeScreen.kt for design issues.
```

**Generate a new screen:**
```
Create a product detail screen for FruitPlaza. It shows a fruit image, name, price, subscription options, and an add to cart CTA.
```

---

## Customizing for Your Project

Open `copilot-instructions.md` and update:
- `## Tech Stack` → add your specific libraries and versions
- The Spacing object values → if your design uses a different base grid
- Color token overrides → once you have your brand palette set up

---

## Resources Used to Build This

- [GitHub Copilot Custom Instructions — Official Docs](https://docs.github.com/en/copilot/customizing-copilot)
- [Material Design 3 — m3.material.io](https://m3.material.io)
- [Material 3 in Compose — developer.android.com](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Awesome Android Agent Skills — github.com/new-silvermoon/awesome-android-agent-skills](https://github.com/new-silvermoon/awesome-android-agent-skills)
- [GitHub Copilot Android Studio Advanced Guide — telefonica.com](https://www.telefonica.com/en/communication-room/blog/github-copilot-android-studio-customization/)
- [5 Tips for Better Copilot Instructions — github.blog](https://github.blog/ai-and-ml/github-copilot/5-tips-for-writing-better-custom-instructions-for-copilot/)
