# Contributing

## Ground Rules

- Keep mixins thin. They should expose game seams, not own feature logic.
- Prefer events and services over direct module-to-module calls.
- Keep config keys stable. Use snake_case IDs, not display labels.
- Avoid adding feature-specific behavior to base abstractions such as `Module`.
- If a packet or rotation flow already has a shared service, use it instead of rolling a private path.

## Code Style

- Follow the existing Java formatting style in the repository.
- Prefer small focused methods over large multi-mode files.
- Add comments only when the code would otherwise be hard to reason about.
- Do not leave silent `catch` blocks unless the failure is genuinely ignorable and documented.

## Verification

Run this before opening a change:

```powershell
.\gradlew.bat build
```

If your change affects runtime behavior, mention the modules or systems you exercised manually.
