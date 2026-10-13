# Project Instructions

## Language
- Always write code, comments, Javadoc, commit messages, and documentation in **English**.

## Git Workflow
- Current working branch: `develop` (integration branch). Feature branches are cut from `develop`.
- Remote: `origin` -> https://github.com/yasmramos/warmup
- GitHub user: `yasmramos` (email: yasmramos95@gmail.com), authenticated via HTTPS token stored in `~/.git-credentials`.

## Commit Messages
- Always use the **Conventional Commits** format, in English:
  `<type>(<scope>): <subject>`
- Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
- Subject in imperative mood, no trailing period, max ~72 characters.
- Example: `feat(core): add lifecycle hook for bean initialization`

## Project Overview
- Warmup: a Java dependency-injection / startup-warming library built with Maven (multi-module).
- Modules: `warmup-core`, `warmup-processor` (annotation processor), `warmup-javafx`, `warmup-benchmarks`.
- Build: `mvn clean install` ; run tests with `mvn test`.
