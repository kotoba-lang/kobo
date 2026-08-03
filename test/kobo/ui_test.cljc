(ns kobo.ui-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [design-quality.audit :as audit]
            [kobo.editor :as editor]
            [kobo.ui :as ui]
            [kobo.workbench :as wb]
            [kuro.terminal :as t]))

(defn- populated []
  (-> (wb/workbench "bafkreiexamplerepocid000000000000000000000000000000000000")
      (wb/open-buffer (wb/buffer "README.md" "# hello\nworld\n"))
      (wb/open-terminal "t1" :terminal-safe)
      (wb/open-terminal "t2" :terminal-build)
      (wb/command-receipt "t1" (t/command ["clojure" "-M:test"])
                          {:exit-code 0 :stdout "ok\n"
                           :stdout-cid "bafkreiaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                           :duration-ms 812})
      (wb/command-receipt "t1" (t/command ["node" "-e" "while(1){}"])
                          {:exit-code 124 :timed-out? true :duration-ms 300})
      (wb/record-denial (assoc (t/denial (wb/terminal (wb/open-terminal
                                                      (wb/workbench "x") "t1" :terminal-safe)
                                                     "t1")
                                         ["secrets/get"])
                               :kuro/argv ["cat" "/etc/passwd"]))
      (wb/add-diagnostics (editor/aiueos-manifest-diagnostics
                           "component.edn" {:aiueos/typo true}))))

(deftest console-renders-a-document
  (let [html (ui/console (populated))]
    (is (str/starts-with? html "<!doctype html>"))
    (is (str/includes? html "<title>kobo — workbench console</title>"))))

(deftest console-shows-what-the-workbench-knows
  (let [html (ui/console (populated))]
    (testing "terminals and their modes"
      (is (str/includes? html "t1"))
      (is (str/includes? html "build")))
    (testing "the effective grant is visible, not implied"
      (is (str/includes? html "repo/read"))
      (is (str/includes? html "cache/write")))
    (testing "receipts carry argv, outcome and content address"
      (is (str/includes? html "clojure -M:test"))
      (is (str/includes? html "812 ms"))
      (is (str/includes? html "bafkreiaaaaaa")))
    (testing "a killed command says the deadline killed it, not just 124"
      (is (str/includes? html "timed out")))
    (testing "refusals are shown, not dropped"
      (is (str/includes? html "cat /etc/passwd"))
      (is (str/includes? html "secrets/get")))
    (testing "buffers and diagnostics"
      (is (str/includes? html "README.md"))
      (is (str/includes? html "Unknown aiueos manifest key")))))

(deftest empty-workbench-says-so
  (testing "an empty section that renders nothing reads as a broken page"
    (let [html (ui/console (wb/workbench "bafkreiempty"))]
      (is (str/includes? html "No terminal session open."))
      (is (str/includes? html "No command has run in this workbench yet."))
      (is (str/includes? html "No command was refused.")))))

(deftest console-has-no-write-surface
  (testing "operator-console convention: nothing here can send anything"
    (let [html (ui/console (populated))]
      (is (not (str/includes? html "<form")))
      (is (not (str/includes? html "<button")))
      (is (not (str/includes? html "<input"))))))

(deftest short-cid-keeps-the-full-value-recoverable
  (is (= "bafkreiaaaaa…" (ui/short-cid "bafkreiaaaaaaaaaaaaaaa")))
  (is (= "short" (ui/short-cid "short")))
  (testing "the full cid stays in the markup even though the label is cut"
    (let [html (ui/console (populated))]
      (is (str/includes?
           html "bafkreiaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")))))

(deftest console-is-scored-not-eyeballed
  (testing "deterministic HIG/WCAG audit (ADR-2607132300)"
    (let [{:keys [overall axes]} (audit/score-page (ui/console (populated)))]
      (is (>= overall 95.0)
          (str "design-quality " overall " — short axes: "
               (pr-str (->> axes (remove #(>= (:score %) 1.0))
                            (mapv (juxt :id :score :finding)))))))))
