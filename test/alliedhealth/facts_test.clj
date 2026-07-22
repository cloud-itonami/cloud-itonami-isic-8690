(ns alliedhealth.facts-test
  (:require [clojure.test :refer [deftest is]]
            [alliedhealth.facts :as facts]))

(deftest known-jurisdictions-have-a-spec-basis
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU" "IND"]]
    (is (some? (facts/spec-basis iso3)) (str iso3 " should have a spec-basis"))
    (is (= 4 (count (:required-evidence (facts/spec-basis iso3)))))))

(deftest india-spec-basis-cites-the-ncahp-act
  (let [india (facts/spec-basis "IND")]
    (is (= "India" (:name india)))
    (is (re-find #"National Commission for Allied and Healthcare Professions Act, 2021"
                 (:legal-basis india)))
    (is (re-find #"Act No\. 14 of 2021" (:legal-basis india)))
    (is (re-find #"s\. 33" (:legal-basis india))
        "should cite the State Register registration section")
    (is (re-find #"indiacode\.nic\.in" (:provenance india)))
    (is (= 4 (count (:required-evidence india))))))

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
