# kobo

`kobo` is the Kotoba **workbench**: code editor, **terminal / console surface**,
durable agent loop model, and audit-oriented repository view (ADR-2606301000).
If you are looking for a terminal, a console, a shell surface, or an IDE-shaped
development environment in this workspace, `kobo` and
[`kuro`](https://github.com/kotoba-lang/kuro) are it. Neither name says so,
which is why this paragraph does.

The model is portable `.cljc`. It does not own filesystem, shell, network,
secrets, browser, or desktop effects. Those are host capabilities.

```text
kobo = editor + terminal + grants + receipts + repo facts
kuro = terminal model used by kobo
```

## Boundaries

| layer | role |
|---|---|
| `kobo.workbench` | panes, buffers, terminal sessions, receipts, denials |
| `kobo.editor` | buffers, deterministic patch application, diagnostics |
| `kobo.grant` | capability intersection and denial explanation |
| `kobo.ui` | the read-only workbench console (kotoba-ui / appkit) |
| `kobo.host.node` | runs a terminal command for real, via `kuro.host.node` |
| `kuro.terminal` | terminal sessions and command receipts |
| host / aiueos | actual process, PTY, container, microVM, filesystem, network |

## Example

```clojure
(require '[kobo.workbench :as wb]
         '[kobo.host.node :as host]     ; nbb / Node only
         '[kobo.ui :as ui])

(-> (wb/workbench "repo-cid")
    (wb/open-buffer (wb/buffer "README.md" "# hello\n"))
    (wb/open-terminal "t1" :terminal-safe)
    (host/run "t1" ["git" "status" "--short"] {:repo-root "."})
    (ui/console))
;; => "<!doctype html>…kobo — workbench console…"
```

A command whose capabilities the session does not grant is **recorded as a
denial**, not dropped — otherwise an empty receipt list means both "refused"
and "never typed".

## The console

`kobo.ui/console` renders the read-only half of the workbench the ADR
describes: terminal sessions with their effective grants, command receipts
(argv, outcome, duration, stdout/stderr CID), refusals, open buffers,
diagnostics.

Following the operator-console convention used across kotoba-lang (`dtn`,
`card`, `ekyc`, `securities`, …), it exposes **no write surface** — no
`<form>`, no `<button>`. Command entry and editing are not wired, and a text
box that cannot send anywhere is worse than an absent one.

Built on the `kotoba-ui` + `appkit` stack: layout from `shell`, colors and
type from tokens, one theme map. Every render is scored by the deterministic
HIG/WCAG audit (`design-quality`, ADR-2607132300) with a ≥ 95 gate in CI —
currently **100.0**, no short axes.

### Not yet built

The ADR's full workbench is five panes (file tree, editor tabs, graph/audit,
terminal tabs, status). This is the read-only subset. Missing: event wiring,
command entry, a PTY (`kuro.host.node` is one-command-in / one-receipt-out —
no ANSI handling, no streaming), the `kobo.agent` durable loop, and
`kobo.repo` (the kotoba-rad/git bridge).

## Tests

```sh
clojure -M:test                 # model + console + design-quality gate (JVM)
npm install && npm run test:host  # Node host wiring (nbb) — really spawns processes
```

Monorepo work can point the deps at sibling checkouts with `-M:local:test`.
