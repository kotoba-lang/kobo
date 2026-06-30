# kobo

`kobo` is the Kotoba workbench: code editor, terminal surface, durable agent
loop model, and audit-oriented repository view.

The implementation is portable `.cljc`. It does not own filesystem, shell,
network, secrets, browser, or desktop effects. Those effects are host
capabilities, normally mediated by aiueos. `kobo` models the workbench state and
the deterministic validation layer.

```text
kobo = editor + terminal + grants + receipts + repo facts
kuro = terminal model used by kobo
```

## Boundaries

| layer | role |
|---|---|
| `kobo.workbench` | panes, buffers, terminal sessions, receipts |
| `kobo.editor` | buffers, deterministic patch application, diagnostics |
| `kobo.grant` | capability intersection and denial explanation |
| `kuro.terminal` | terminal sessions and command receipts |
| host / aiueos | actual process, PTY, container, microVM, filesystem, network |

## Example

```clojure
(require '[kobo.workbench :as wb])

(def w
  (-> (wb/workbench "repo-cid")
      (wb/open-buffer (wb/buffer "README.md" "# hello\n"))
      (wb/open-terminal "t1" :terminal-safe)))
```

## Tests

```sh
clojure -M:test
```
