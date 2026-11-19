# End-to-end tests (Playwright)

Clicks through the Angular frontend in Chromium. A standalone npm package, so the app's
`package.json` stays untouched.

## Setup

```bash
cd frontend/e2e
npm ci
npx playwright install chromium
```

## Run

```bash
npm test                          # mock projects: production build, «Demo-Daten» on, 1440×900 and 1280×720
npx playwright test --project=mock-1440            # one viewport only
npx playwright test tests/chat.spec.ts             # one spec
npm run test:headed               # watch it click (one worker)
npm run test:ui                   # Playwright UI mode
npm run report                    # open the HTML report of the last run
E2E_SKIP_BUILD=1 npm test         # reuse ../dist instead of building again
```

Before the tests, the web server builds the app (`npm --prefix .. run build`, after `npm --prefix .. ci`
if `../node_modules` is missing) and serves `../dist/treasury-kompass/browser` with `serve.mjs` on
port 4400 (`E2E_PORT` to change). `serve.mjs` mimics the Vercel deployment: static files, SPA fallback
to `index.html` for app routes, and `/api/*` and `/agent/*` answered with a plain-text 404.

### Against the real stack

Opt-in, only when `E2E_STACK_URL` is set. Runs the tests tagged `@stack` with «Demo-Daten» off
against the docker-compose gateway. The gateway serves the same `../dist` folder, which the web
server builds first, so the stack tests the current frontend code.

```bash
docker compose up -d              # from the repo root; gateway on http://localhost:8088
cd frontend/e2e
E2E_STACK_URL=http://localhost:8088 npx playwright test --project=stack
```

The stack run asks exactly one real chat question (agent test case 3), because every question calls
the paid LLM. The other stack tests only read.

## Output

| Folder                   | Content                                                                           |
| ------------------------ | --------------------------------------------------------------------------------- |
| `screenshots/<project>/` | Full-page screenshots of every screen and key state (documentation, not compared) |
| `playwright-report/`     | HTML report, axe results attached per screen                                      |
| `test-results/`          | Traces and failure screenshots                                                    |

All three are git-ignored.

## Structure

| Path                      | Content                                                                                                           |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `tests/*.spec.ts`         | One spec per area: start, profilierung, dashboard, beratung, chat, i18n, layout, backend-unavailable, a11y, stack |
| `support/pages/`          | Page objects with the locators (roles and labels first, the agreed `data-testid`s otherwise)                      |
| `support/fixtures.ts`     | `test` with page objects, `snap()` and a guard that fails a test on console errors                                |
| `support/demo-clients.ts` | Expected values of the demo clients (`docs/regelwerk.md` section 9) and chat cases                                |
| `support/flows.ts`        | Multi-screen flows, e.g. preparing a case for approval                                                            |

The mock backend keeps its state only until the next full page load. Flows across screens therefore
navigate with in-app links, never with `page.goto`.

## Conventions

- Accessibility: axe fails the run only on `critical` violations; `serious` ones appear as
  annotations in the report and in the console output (`[axe serious] …`).
- A test that provokes errors on purpose calls `allowConsoleErrors()`.
