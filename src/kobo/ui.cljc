(ns kobo.ui
  "The workbench console — the screen ADR-2606301000 describes, at the size it
  can honestly be built today.

  The ADR's target layout is a five-pane workbench (file tree, editor tabs,
  graph/audit, terminal tabs). This renders the read-only half of it: what the
  workbench *knows* — terminal sessions and their effective grants, command
  receipts, open buffers, diagnostics. Editing and command entry are not here,
  because `kobo` has no event wiring and `kuro.host.node` has no PTY; a text
  box that cannot send anywhere is worse than an absent one.

  So this follows the operator-console convention used across kotoba-lang
  (`dtn`, `card`, `ekyc`, `securities`, …): pure data → markup, and **no write
  surface** — no `<form>`, no `<button>`. Every value shown comes from a
  receipt that already happened.

  Pure `.cljc` hiccup on the kotoba-ui stack (single require point, layout
  from shell, colors from the theme map)."
  (:require [appkit.core :as appkit]
            [clojure.string :as str]
            [kotoba-ui.core :as ui]))

(def theme
  "One map, per the design-system contract — the only place a hex belongs.

  `:auto` rather than a forced dark: the console is read at a desk in the
  morning and at 2am, and the tokens already carry both schemes."
  {:accent "#3C7DD9" :appearance :auto})

(def ^:private mode-labels
  {:terminal-safe "safe"
   :terminal-build "build"
   :terminal-agent "agent"
   :terminal-host "host"})

(defn short-cid
  "First 12 characters of a CID for display. The full value stays in the
  row's title attribute — a truncated hash that cannot be recovered is
  decoration, not evidence."
  [cid]
  (let [s (str cid)]
    (if (> (count s) 12) (str (subs s 0 12) "…") s)))

(defn- capability-chips [caps]
  (map #(ui/chip %) (sort caps)))

(defn- outcome-label
  "How a command ended, in words. An exit code alone does not distinguish
  \"returned 124\" from \"the deadline killed it\"."
  [r]
  (cond
    (:kuro/timed-out? r) "timed out"
    (:kuro/truncated? r) "output capped"
    (:kuro/error r) (:kuro/error r)
    (zero? (:kuro/exit-code r)) "ok"
    :else (str "exit " (:kuro/exit-code r))))

(defn terminal-row [[id sess]]
  (ui/list-row
   [:span
    [:span {:class "hig-headline"} (str id)]
    [:span {:class "hig-caption1"} " " (:kuro/cwd sess)]
    [:div (capability-chips (get-in sess [:kuro/grant :capabilities] #{}))]]
   {:trailing (ui/badge (mode-labels (:kuro/mode sess) "?"))}))

(defn receipt-row [r]
  (ui/list-row
   [:span
    [:code (str/join " " (:kuro/argv r))]
    [:span {:class "hig-caption1"}
     " " (outcome-label r)
     (when-let [ms (:kuro/duration-ms r)] (str " · " ms " ms"))
     (when-let [cid (:kuro/stdout-cid r)]
       [:span {:title cid} (str " · stdout " (short-cid cid))])]]
   {:trailing (ui/badge (str (:kuro/exit-code r)))}))

(defn buffer-row [[path buf]]
  (ui/list-row
   [:span {:class "hig-body"} (str path)]
   {:trailing (ui/badge (str (count (str/split-lines (str (:kobo.buffer/text buf ""))))
                             " lines"))}))

(defn denial-row [d]
  (ui/list-row
   [:span
    [:code (str/join " " (:kuro/argv d))]
    [:span {:class "hig-caption1"}
     " needs " (str/join ", " (:kuro/missing d))]]
   {:trailing (ui/badge "denied")}))

(defn diagnostic-row [d]
  (ui/list-row
   [:span
    [:span {:class "hig-headline"} (str (:kobo.diagnostic/path d))]
    [:span {:class "hig-caption1"} " " (str (:kobo.diagnostic/message d))]]
   {:trailing (ui/badge (name (:kobo.diagnostic/severity d :info)))}))

(defn- empty-note [text]
  [:p {:class "hig-footnote"} text])

(defn- listing
  "A section holding a list, or an explicit note when there is nothing.

  An empty section that renders nothing reads as a broken page; an empty
  section that says why reads as a true one."
  [title rows empty-text]
  (ui/section {:title title}
    (if (seq rows)
      (appkit/list-view rows)
      (empty-note empty-text))))

(defn view
  "The workbench console as hiccup — mountable as-is, or wrapped by `console`."
  [wb]
  (ui/app-shell
   {:nav (ui/nav-bar "kobo"
                     {:trailing [(ui/badge (short-cid (:kobo/repo-root-cid wb)))]})}
   (listing "Terminals" (map terminal-row (sort-by key (:kobo/terminals wb)))
            "No terminal session open.")
   (listing "Receipts" (map receipt-row (:kobo/receipts wb))
            "No command has run in this workbench yet.")
   (listing "Denied" (map denial-row (:kobo/denials wb))
            "No command was refused.")
   (listing "Buffers" (map buffer-row (sort-by key (:kobo/buffers wb)))
            "No buffer open.")
   (listing "Diagnostics" (map diagnostic-row (:kobo/diagnostics wb))
            "No diagnostics.")))

(defn console
  "The complete HTML document for a workbench. opts: `:theme` (defaults to
  `theme`), plus anything `kotoba-ui.core/->page` takes."
  ([wb] (console wb {}))
  ([wb opts]
   (ui/->page (merge {:title "kobo — workbench console"
                      :description "Terminal sessions, grants, and command receipts."
                      :theme theme}
                     opts)
              (view wb))))
