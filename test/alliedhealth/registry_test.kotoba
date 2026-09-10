(ns alliedhealth.registry-test
  (:require [clojure.test :refer [deftest is]]
            [alliedhealth.registry :as r]))

;; ----------------------------- treatment-outside-scope-of-practice? -----------------------------

(deftest not-outside-scope-when-treatment-is-within-scope
  (is (not (r/treatment-outside-scope-of-practice?
            {:proposed-treatment :manual-therapy
             :practitioner-scope-of-practice #{:manual-therapy :therapeutic-exercise}}))))

(deftest outside-scope-when-treatment-is-not-in-scope-set
  (is (r/treatment-outside-scope-of-practice?
       {:proposed-treatment :spinal-manipulation
        :practitioner-scope-of-practice #{:manual-therapy :therapeutic-exercise}})))

(deftest outside-scope-is-true-on-missing-fields
  (is (r/treatment-outside-scope-of-practice? {}))
  (is (r/treatment-outside-scope-of-practice? {:proposed-treatment :manual-therapy})))

;; ----------------------------- register-treatment-session -----------------------------

(deftest session-is-a-draft-not-a-real-session
  (let [result (r/register-treatment-session "encounter-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest session-assigns-session-number
  (let [result (r/register-treatment-session "encounter-1" "JPN" 7)]
    (is (= (get result "session_number") "JPN-TX-000007"))
    (is (= (get-in result ["record" "encounter_id"]) "encounter-1"))
    (is (= (get-in result ["record" "kind"]) "treatment-session-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest session-validation-rules
  (is (thrown? Exception (r/register-treatment-session "" "JPN" 0)))
  (is (thrown? Exception (r/register-treatment-session "encounter-1" "" 0)))
  (is (thrown? Exception (r/register-treatment-session "encounter-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-treatment-session "encounter-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-treatment-session "encounter-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-TX-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-TX-000001" (get-in hist2 [1 "record_id"])))))
