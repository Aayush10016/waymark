# User Personas

## Primary persona: Framework Debugger

**Example:** Maya, backend or full-stack engineer.

- Debugs framework configuration, authentication, database, dependency, build, and integration issues several times per week.
- Uses Chrome across search engines, official documentation, Stack Overflow, GitHub, Reddit, YouTube, and AI tools.
- Often remembers having solved something similar but not the exact configuration change.
- Current workaround: search again, inspect old commits, reopen chat history, or ask a teammate.
- Privacy concern: client URLs, internal systems, tokens in URLs, proprietary error details.
- Activation: records one real solution with minimal effort.
- Retention moment: Trail proactively surfaces that solution during a recurrence.

MVP is designed for this persona.

## Secondary validation persona: Computer Science Student

- Encounters recurring environment, dependency, compiler, deployment, and coursework issues.
- Has less stable terminology, making similarity useful.
- May share devices and is more price-sensitive.
- Validates clarity and onboarding after professional-engineer dogfood.

## Secondary validation persona: Open-Source Contributor

- Moves between unfamiliar repositories, issues, documentation, and CI failures.
- Would benefit from remembering local setup and project-specific fixes.
- Repository and terminal context are incomplete in the browser-only MVP, so this is not the primary launch promise.

## Persona labels that do not create separate products

Backend engineer and full-stack developer are behavioral variants of the primary persona. They do not justify separate feature sets.

## Excluded personas

- Managers seeking engineering activity analytics.
- Teams seeking an issue tracker or knowledge base.
- Users seeking a general AI coding assistant.
- Regulated or privileged engineering environments requiring enterprise controls.
- Developers whose debugging happens almost entirely in terminal/IDE and rarely in the browser.

## Beta recruitment

Recruit 15–25 Chrome-using developers who:

- debug web-heavy technical problems at least three times per week;
- have repeated a fix because they forgot the original solution;
- use at least three source types in a typical session;
- accept metadata capture after reading the disclosure;
- will participate long enough to create several solved Problem Sessions.
