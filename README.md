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

**Status: R2** — the console renders real terminal output (ANSI-aware) and the
workbench holds live, running commands. Command entry and editing are not
wired; see *Not yet built*.

## Boundaries

| layer | role |
|---|---|
| `kobo.workbench` | panes, buffers, terminal sessions, receipts, denials |
| `kobo.editor` | buffers, deterministic patch application, diagnostics |
| `kobo.grant` | capability intersection and denial explanation |
| `kobo.ui` | the read-only workbench console (kotoba-ui / appkit) |
| `kobo.host.node` | runs one command to completion, via `kuro.host.node` |
| `kobo.host.stream-node` | runs a command **live** — output, stdin, kill — via `kuro.host.stream-node` |
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

## Running a command live

`kobo.host.node/run` returns only when the command is over. For anything that
takes time, put the workbench in an atom and stream into it:

```clojure
(require '[kobo.host.stream-node :as sh])   ; nbb / Node only

(def w (atom (-> (wb/workbench "repo-cid") (wb/open-terminal "t1" :terminal-safe))))

(def h (sh/start w "t1" ["npm" "test"] {:repo-root "."}))
((:write h) "y\n")   ; stdin
((:kill h))           ; stop it
```

While it runs it is in `:kobo/running` (the live `kuro.stream` value); when it
exits it moves to `:kobo/receipts`. The console renders both, in separate
sections — "not finished yet" and "finished, exit 0" are different facts and
must not share a row.

## The console

`kobo.ui/console` renders the read-only half of the workbench the ADR
describes: terminal sessions with their effective grants, command receipts
(argv, outcome, duration, stdout/stderr CID), refusals, open buffers,
diagnostics — and **command output with its ANSI colours interpreted**, via
`kuro.ansi`.

Terminal colours are mapped onto HIG palette tokens, never to inline hex: the
console keeps the design system's light/dark switching and contrast
guarantees. Two deliberate infidelities — terminal `black` and `white` mean
"the opposite of this terminal's background", not absolute colours, so both
render as the default label colour. 256-colour and truecolour spans lose their
colour but keep their text. **A readable approximation beats an unreadable
faithful one.** Output is capped at 200 lines on screen, and the cap says how
many lines it dropped.

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
terminal tabs, status). This is the read-only subset. Missing: event wiring and command entry (the console is still read-only), a
real PTY (the child is on pipes, so full-screen programs do not work), the
`kobo.agent` durable loop, `kobo.repo` (the kotoba-rad/git bridge), the file
tree, and the LSP bridge.

## Tests

```sh
clojure -M:test                 # model + console + design-quality gate (JVM)
npm install && npm run test:host  # Node host wiring (nbb) — really spawns processes
```

Monorepo work can point the deps at sibling checkouts with `-M:local:test`.
