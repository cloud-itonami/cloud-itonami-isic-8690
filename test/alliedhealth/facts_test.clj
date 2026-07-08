(ns alliedhealth.facts-test
  (:require [clojure.test :refer [deftest is]]
            [alliedhealth.facts :as facts]))

(deftest known-jurisdictions-have-a-spec-basis
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU"]]
    (is (some? (facts/spec-basis iso3)) (str iso3 " should have a spec-basis"))
    (is (= 4 (count (:required-evidence (facts/spec-basis iso3)))))))

(deftest unknown-jurisdiction-has-no-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-is-honest
  (let [c (facts/coverage ["JPN" "USA" "ATL"])]
    (is (= 3 (:requested c)))
    (is (= 2 (:covered c)))
    (is (= ["JPN" "USA"] (:covered-jurisdictions c)))
    (is (= ["ATL"] (:missing-jurisdictions c)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [checklist (facts/evidence-checklist "JPN")]
    (is (= 4 (count checklist)))
    (is (facts/required-evidence-satisfied? "JPN" checklist))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest checklist))))
    (is (not (facts/required-evidence-satisfied? "ATL" checklist)))))
