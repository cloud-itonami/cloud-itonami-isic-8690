(ns alliedhealth.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [alliedhealth.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sato Kenji" (:patient-name (store/encounter s "encounter-1"))))
      (is (= "JPN" (:jurisdiction (store/encounter s "encounter-1"))))
      (is (= :manual-therapy (:proposed-treatment (store/encounter s "encounter-1"))))
      (is (= #{:manual-therapy :therapeutic-exercise :ultrasound}
             (:practitioner-scope-of-practice (store/encounter s "encounter-1"))))
      (is (false? (:credential-not-current? (store/encounter s "encounter-1"))))
      (is (= :spinal-manipulation (:proposed-treatment (store/encounter s "encounter-3"))))
      (is (true? (:credential-not-current? (store/encounter s "encounter-4"))))
      (is (false? (:treated? (store/encounter s "encounter-1"))))
      (is (= ["encounter-1" "encounter-2" "encounter-3" "encounter-4"]
             (mapv :id (store/all-encounters s))))
      (is (nil? (store/credential-of s "encounter-1")))
      (is (nil? (store/assessment-of s "encounter-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/session-history s)))
      (is (zero? (store/next-sequence s "JPN")))
      (is (false? (store/encounter-already-treated? s "encounter-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :encounter/upsert
                                 :value {:id "encounter-1" :patient-name "Sato Kenji"}})
        (is (= "Sato Kenji" (:patient-name (store/encounter s "encounter-1"))))
        (is (= #{:manual-therapy :therapeutic-exercise :ultrasound}
               (:practitioner-scope-of-practice (store/encounter s "encounter-1"))) "unrelated field preserved"))
      (testing "assessment / credential payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["encounter-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "encounter-1")))
        (store/commit-record! s {:effect :credential/set :path ["encounter-1"]
                                 :payload {:encounter-id "encounter-1" :credential-not-current? false}})
        (is (= {:encounter-id "encounter-1" :credential-not-current? false} (store/credential-of s "encounter-1"))))
      (testing "treatment-session administration drafts a record and advances the sequence"
        (store/commit-record! s {:effect :encounter/mark-treated :path ["encounter-1"]})
        (is (= "JPN-TX-000000" (get (first (store/session-history s)) "record_id")))
        (is (= "treatment-session-draft" (get (first (store/session-history s)) "kind")))
        (is (true? (:treated? (store/encounter s "encounter-1"))))
        (is (= 1 (count (store/session-history s))))
        (is (= 1 (store/next-sequence s "JPN")))
        (is (true? (store/encounter-already-treated? s "encounter-1")))
        (is (false? (store/encounter-already-treated? s "encounter-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/encounter s "nope")))
    (is (= [] (store/all-encounters s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/session-history s)))
    (is (zero? (store/next-sequence s "JPN")))
    (store/with-encounters s {"x" {:id "x" :patient-name "n"
                                   :proposed-treatment :manual-therapy
                                   :practitioner-scope-of-practice #{:manual-therapy}
                                   :credential-not-current? false
                                   :treated? false :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:patient-name (store/encounter s "x"))))))
