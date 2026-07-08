(ns alliedhealth.governor-contract-test
  "The governor contract as executable tests -- the allied-health
  analog of `cloud-itonami-isic-6512`'s `casualty.governor-contract-
  test`. The single invariant under test:

    AlliedHealth-LLM never administers a treatment session the Allied
    Health Governor would reject, `:actuation/administer-treatment-
    session` NEVER auto-commits at any phase, `:encounter/intake` (no
    direct capital risk) MAY auto-commit when clean, and every
    decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [alliedhealth.store :as store]
            [alliedhealth.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :licensed-professional :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :assessment/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :encounter/intake :subject "encounter-1"
                   :patch {:id "encounter-1" :patient-name "Sato Kenji"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sato Kenji" (:patient-name (store/encounter db "encounter-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest assessment-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :assessment/verify :subject "encounter-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "encounter-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "an assessment/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :assessment/verify :subject "encounter-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "encounter-1")) "no assessment written"))))

(deftest administer-treatment-session-without-assessment-is-held
  (testing "actuation/administer-treatment-session before any assessment verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/administer-treatment-session :subject "encounter-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest treatment-outside-scope-of-practice-is-held
  (testing "a proposed treatment falling outside the practitioner's own recorded scope of practice -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "encounter-3")
          res (exec-op actor "t5" {:op :actuation/administer-treatment-session :subject "encounter-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:treatment-outside-scope-of-practice} (-> (store/ledger db) last :basis)))
      (is (empty? (store/session-history db))))))

(deftest credential-not-current-is-held-and-unoverridable
  (testing "a not-current practitioner credential on an encounter -> HOLD, and never reaches request-approval -- exercised via :credential/screen DIRECTLY, not via the actuation op against an unscreened encounter (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's, water's, telecom's, aerospace's, recovery's, consulting's, union's, congregation's, fab's, energy's, care's, navigator's, learning's, banking's, advertising's, polling's, research's, design's, nursing's and sports's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :credential/screen :subject "encounter-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:credential-not-current} (-> (store/ledger db) first :basis)))
      (is (nil? (store/credential-of db "encounter-4")) "no clearance written"))))

(deftest administer-treatment-session-always-escalates-then-human-decides
  (testing "a clean, fully-assessed encounter still ALWAYS interrupts for human approval -- actuation/administer-treatment-session is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "encounter-1")
          r1 (exec-op actor "t7" {:op :actuation/administer-treatment-session :subject "encounter-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, treatment-session record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:treated? (store/encounter db "encounter-1"))))
          (is (= 1 (count (store/session-history db))) "one draft treatment-session record"))))))

(deftest administer-treatment-session-double-administration-is-held
  (testing "administering a treatment session to the same encounter twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "encounter-1")
          _ (exec-op actor "t8a" {:op :actuation/administer-treatment-session :subject "encounter-1"} operator)
          _ (approve! actor "t8a")
          res (exec-op actor "t8" {:op :actuation/administer-treatment-session :subject "encounter-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-treated} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/session-history db))) "still only the one earlier administration"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :encounter/intake :subject "encounter-1"
                          :patch {:id "encounter-1" :patient-name "Sato Kenji"}} operator)
      (exec-op actor "b" {:op :assessment/verify :subject "encounter-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
