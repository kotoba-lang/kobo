(ns kobo.ui-source-test
  "Design-system discipline checked against the source text, not the render.

  The rendered page scores 100 either way — the audit sees CSS variables, not
  who authored them. These rules (rule 2 and rule 5 of the kotoba-ui contract:
  no raw hex / px font-size / font-family in app code, theme is one map) are
  only visible in the source, so this is a `.clj` test that reads the file.
  Kept out of the `.cljc` suite because `slurp` has no ClojureScript analogue."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def ^:private src (slurp "src/kobo/ui.cljc"))

(deftest theme-map-is-the-only-place-a-hex-belongs
  (let [body (str/replace src #"\{:accent \"#[0-9A-Fa-f]{6}\"[^}]*\}" "")]
    (is (nil? (re-find #"#[0-9A-Fa-f]{6}" body)))))

(deftest no-app-authored-typography
  (testing "type comes from the 11 HIG text styles, never from app CSS"
    (is (nil? (re-find #"font-size" src)))
    (is (nil? (re-find #"font-family" src)))))

(deftest single-require-point
  (testing "apps require kotoba-ui.core (+ appkit.core), never the layers below"
    (is (nil? (re-find #"\[liquid-glass\." src)))
    (is (nil? (re-find #"\[shitsuke\." src)))))

(deftest layout-comes-from-shell
  (testing "no hand-written layout — the scaffolds are used, not reimplemented"
    (is (str/includes? src "ui/app-shell"))
    (is (str/includes? src "ui/section"))
    (is (nil? (re-find #"grid-template|display:\s*flex" src)))))
