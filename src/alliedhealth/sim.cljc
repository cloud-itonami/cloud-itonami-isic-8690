(ns alliedhealth.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean encounter through
  intake -> assessment verification -> credential screening ->
  treatment-session proposal (always escalates) -> human approval ->
  commit, then shows four HARD holds (a jurisdiction with no spec-
  basis, a proposed treatment falling outside the treating
  practitioner's own recorded scope of practice, a not-current
  practitioner credential screened directly via `:credential/screen`
  [never via an actuation op against an unscreened encounter -- see
  this actor's own governor ns docstring / the lesson `parksafety`'s
  ADR-2607071922 Decision 5, `eldercare`'s, `museum`'s,
  `conservation`'s, `salon`'s, `entertainment`'s, `casework`'s,
  `hospital`'s, `facility`'s, `school`'s, `association`'s, `leasing`'s,
  `behavioral`'s, `secondary`'s, `card`'s, `water`'s, `telecom`'s,
  `aerospace`'s, `recovery`'s, `consulting`'s, `union`'s,
  `congregation`'s, `fab`'s, `energy`'s, `care`'s, `navigator`'s,
  `learning`'s, `banking`'s, `advertising`'s, `polling`'s, `research`'s,
  `design`'s, `nursing`'s and `sports`'s ADR-0001s already recorded],
  and a double treatment-session administration of an already-
  processed encounter) that never reach a human at all, and prints the
  audit ledger + the draft treatment-session records."
  (:require [langgraph.graph :as g]
            [alliedhealth.store :as store]
            [alliedhealth.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :licensed-professional :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== encounter/intake encounter-1 (JPN, clean; treatment within scope, credential current) ==")
    (println (exec! actor "t1" {:op :encounter/intake :subject "encounter-1"
                                :patch {:id "encounter-1" :patient-name "Sato Kenji"}} operator))

    (println "== assessment/verify encounter-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :assessment/verify :subject "encounter-1"} operator))
    (println (approve! actor "t2"))

    (println "== credential/screen encounter-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :credential/screen :subject "encounter-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/administer-treatment-session encounter-1 (always escalates -- actuation/administer-treatment-session) ==")
    (let [r (exec! actor "t4" {:op :actuation/administer-treatment-session :subject "encounter-1"} operator)]
      (println r)
      (println "-- human licensed professional approves --")
      (println (approve! actor "t4")))

    (println "== assessment/verify encounter-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t5" {:op :assessment/verify :subject "encounter-2" :no-spec? true} operator))

    (println "== assessment/verify encounter-3 (escalates -- human approves; sets up the scope-of-practice test) ==")
    (println (exec! actor "t6" {:op :assessment/verify :subject "encounter-3"} operator))
    (println (approve! actor "t6"))

    (println "== actuation/administer-treatment-session encounter-3 (spinal-manipulation outside scope-of-practice -> HARD hold) ==")
    (println (exec! actor "t7" {:op :actuation/administer-treatment-session :subject "encounter-3"} operator))

    (println "== credential/screen encounter-4 (not-current -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t8" {:op :credential/screen :subject "encounter-4"} operator))

    (println "== actuation/administer-treatment-session encounter-1 AGAIN (double-administration -> HARD hold) ==")
    (println (exec! actor "t9" {:op :actuation/administer-treatment-session :subject "encounter-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft treatment-session records ==")
    (doseq [r (store/session-history db)] (println r))))
