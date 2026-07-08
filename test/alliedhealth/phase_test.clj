(ns alliedhealth.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/administer-treatment-session` must NEVER be
  a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [alliedhealth.phase :as phase]))

(deftest administer-treatment-session-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real treatment-session administration"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/administer-treatment-session))
          (str "phase " n " must not auto-commit :actuation/administer-treatment-session")))))

(deftest credential-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :credential/screen))
          (str "phase " n " must not auto-commit :credential/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":encounter/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:encounter/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :encounter/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/administer-treatment-session} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :encounter/intake} :commit)))))
