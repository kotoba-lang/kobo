(ns kobo.workbench
  (:require [kobo.editor :as editor]
            [kuro.terminal :as terminal]))

(defn workbench
  ([repo-root-cid] (workbench repo-root-cid {}))
  ([repo-root-cid attrs]
   (merge {:kobo/type :kobo/workbench
           :kobo/repo-root-cid repo-root-cid
           :kobo/buffers {}
           :kobo/terminals {}
           :kobo/receipts []
           :kobo/diagnostics []}
          attrs)))

(defn buffer [path text]
  (editor/buffer path text))

(defn open-buffer [wb buf]
  (assoc-in wb [:kobo/buffers (:kobo.buffer/path buf)] buf))

(defn update-buffer [wb path f & args]
  (apply update-in wb [:kobo/buffers path] f args))

(defn apply-patch [wb p]
  (update-buffer wb (:kobo.patch/path p) editor/apply-patch p))

(defn open-terminal
  ([wb id mode] (open-terminal wb id mode {}))
  ([wb id mode attrs]
   (let [sess (terminal/session id (:kobo/repo-root-cid wb) mode attrs)]
     (assoc-in wb [:kobo/terminals id] sess))))

(defn terminal [wb id]
  (get-in wb [:kobo/terminals id]))

(defn command-receipt [wb terminal-id cmd result]
  (let [sess (terminal wb terminal-id)
        rcpt (terminal/receipt sess cmd result)]
    (update wb :kobo/receipts conj rcpt)))

(defn add-diagnostics [wb diagnostics]
  (update wb :kobo/diagnostics into diagnostics))

(defn diagnose-aiueos-manifest [wb path manifest]
  (add-diagnostics wb (editor/aiueos-manifest-diagnostics path manifest)))

(defn kotoba-facts [wb]
  (concat
   [{:kotoba/type :kobo/workbench
     :kotoba/id [:kobo/workbench (:kobo/repo-root-cid wb)]
     :kobo/repo-root-cid (:kobo/repo-root-cid wb)}]
   (map (fn [[path buf]]
          {:kotoba/type :kobo/buffer
           :kotoba/id [:kobo/buffer (:kobo/repo-root-cid wb) path]
           :kobo/buffer buf})
        (:kobo/buffers wb))
   (map terminal/receipt-fact (:kobo/receipts wb))))
