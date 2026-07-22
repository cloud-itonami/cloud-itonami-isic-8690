(ns alliedhealth.store
  "SSoT for the allied-health actor, behind a `Store` protocol so the
  backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/alliedhealth/store_contract_test.clj), which is the whole
  point: the actor, the Allied Health Governor and the audit ledger
  never know which SSoT they run on.

  Like `clinic.store`'s/`credit.store`'s/`accounting.store`'s simpler
  entities, an ENCOUNTER is acted on directly by the ONE actuation op
  -- no dynamically-filed sub-record, and the double-administration
  guard checks a dedicated `:treated?` boolean rather than a `:status`
  value, the same discipline `clinic.governor`'s/`accounting.
  governor`'s/`marketadmin.governor`'s guards establish.

  The ledger stays append-only on every backend: 'which encounter was
  screened for a current practitioner credential, which treatment
  session was administered, on what jurisdictional basis, approved by
  whom' is always a query over an immutable log -- the audit trail a
  patient trusting a practice needs, and the evidence an operator
  needs if a treatment session is later disputed."
  (:require [alliedhealth.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (encounter [s id])
  (all-encounters [s])
  (credential-of [s encounter-id] "committed credential screening verdict for an encounter, or nil")
  (assessment-of [s encounter-id] "committed jurisdiction assessment, or nil")
  (ledger [s])
  (session-history [s] "the append-only treatment-session-administration history (alliedhealth.registry drafts)")
  (next-sequence [s jurisdiction] "next session-number sequence for a jurisdiction")
  (encounter-already-treated? [s encounter-id] "has this encounter already had a treatment session administered?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-encounters [s encounters] "replace/seed the encounter directory (map id->encounter)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained encounter set so the actor + tests run
  offline."
  []
  {:encounters
   {"encounter-1" {:id "encounter-1" :patient-name "Sato Kenji"
                   :proposed-treatment :manual-therapy
                   :practitioner-scope-of-practice #{:manual-therapy :therapeutic-exercise :ultrasound}
                   :credential-not-current? false
                   :treated? false :jurisdiction "JPN" :status :intake}
    "encounter-2" {:id "encounter-2" :patient-name "Atlantis Doe"
                   :proposed-treatment :manual-therapy
                   :practitioner-scope-of-practice #{:manual-therapy :therapeutic-exercise :ultrasound}
                   :credential-not-current? false
                   :treated? false :jurisdiction "ATL" :status :intake}
    "encounter-3" {:id "encounter-3" :patient-name "鈴木花子"
                   :proposed-treatment :spinal-manipulation
                   :practitioner-scope-of-practice #{:manual-therapy :therapeutic-exercise :ultrasound}
                   :credential-not-current? false
                   :treated? false :jurisdiction "JPN" :status :intake}
    "encounter-4" {:id "encounter-4" :patient-name "田中一郎"
                   :proposed-treatment :manual-therapy
                   :practitioner-scope-of-practice #{:manual-therapy :therapeutic-exercise :ultrasound}
                   :credential-not-current? true
                   :treated? false :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- administer-treatment-session!
  "Backend-agnostic `:encounter/mark-treated` -- looks up the encounter
  via the protocol and drafts the treatment-session-administration
  record, and returns {:result .. :encounter-patch ..} for the caller
  to persist."
  [s encounter-id]
  (let [e (encounter s encounter-id)
        seq-n (next-sequence s (:jurisdiction e))
        result (registry/register-treatment-session encounter-id (:jurisdiction e) seq-n)]
    {:result result
     :encounter-patch {:treated? true
                       :session-number (get result "session_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (encounter [_ id] (get-in @a [:encounters id]))
  (all-encounters [_] (sort-by :id (vals (:encounters @a))))
  (credential-of [_ id] (get-in @a [:credential id]))
  (assessment-of [_ encounter-id] (get-in @a [:assessments encounter-id]))
  (ledger [_] (:ledger @a))
  (session-history [_] (:sessions @a))
  (next-sequence [_ jurisdiction] (get-in @a [:sequences jurisdiction] 0))
  (encounter-already-treated? [_ encounter-id] (boolean (get-in @a [:encounters encounter-id :treated?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :encounter/upsert
      (swap! a update-in [:encounters (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :credential/set
      (swap! a assoc-in [:credential (first path)] payload)

      :encounter/mark-treated
      (let [encounter-id (first path)
            {:keys [result encounter-patch]} (administer-treatment-session! s encounter-id)
            jurisdiction (:jurisdiction (encounter s encounter-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:sequences jurisdiction] (fnil inc 0))
                       (update-in [:encounters encounter-id] merge encounter-patch)
                       (update :sessions registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-encounters [s encounters] (when (seq encounters) (swap! a assoc :encounters encounters)) s))

(defn seed-db
  "A MemStore seeded with the demo encounter set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :credential {} :ledger [] :sequences {}
                           :sessions []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Compound values (assessment/credential payloads, ledger facts,
  treatment-session records, and the encounter's own
  proposed-treatment/practitioner-scope-of-practice fields) are stored
  as EDN strings so `langchain.db` doesn't expand them into
  sub-entities -- the same convention every sibling actor's store
  uses. The identity-schema builder, EDN-blob codec, field-spec-driven
  entity (de)serialization and seq-keyed event-log read/append are the
  shared kotoba-lang/langchain-store machinery (ADR-2607141600) -- the
  seam ~190 actors hand-roll; this store keeps only its domain wiring."
  (ls/identity-schema
   [:encounter/id :assessment/encounter-id :credential/encounter-id
    :ledger/seq :session/seq :sequence/jurisdiction]))

;; The encounter entity's field spec drives map->tx / pull->map /
;; pull-pattern (langchain-store.core) -- the same present-keys-only
;; tx-building and blob-decode-with-default read shaping the
;; hand-written encounter->tx/pull->encounter used to do by hand.
(def ^:private encounter-spec
  {:id                             {:attr :encounter/id}
   :patient-name                   {:attr :encounter/patient-name}
   :proposed-treatment             {:attr :encounter/proposed-treatment :blob? true}
   :practitioner-scope-of-practice {:attr :encounter/practitioner-scope-of-practice :blob? true :default #{}}
   :credential-not-current?        {:attr :encounter/credential-not-current? :coerce boolean}
   :treated?                       {:attr :encounter/treated? :coerce boolean}
   :jurisdiction                   {:attr :encounter/jurisdiction}
   :status                         {:attr :encounter/status}
   :session-number                 {:attr :encounter/session-number}})

(defn- encounter->tx [encounter] (ls/map->tx encounter-spec encounter))

(def ^:private encounter-pull (ls/pull-pattern encounter-spec))

(defn- pull->encounter [m] (ls/pull->map encounter-spec :id m))

(defrecord DatomicStore [conn]
  Store
  (encounter [_ id]
    (pull->encounter (d/pull (d/db conn) encounter-pull [:encounter/id id])))
  (all-encounters [_]
    (->> (d/q '[:find [?id ...] :where [?e :encounter/id ?id]] (d/db conn))
         (map #(pull->encounter (d/pull (d/db conn) encounter-pull [:encounter/id %])))
         (sort-by :id)))
  (credential-of [_ id]
    (ls/dec* (d/q '[:find ?p . :in $ ?eid
                :where [?k :credential/encounter-id ?eid] [?k :credential/payload ?p]]
              (d/db conn) id)))
  (assessment-of [_ encounter-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?eid
                :where [?a :assessment/encounter-id ?eid] [?a :assessment/payload ?p]]
              (d/db conn) encounter-id)))
  (ledger [_] (ls/read-stream conn :ledger/seq :ledger/fact))
  (session-history [_] (ls/read-stream conn :session/seq :session/record))
  (next-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :sequence/jurisdiction ?j] [?e :sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (encounter-already-treated? [s encounter-id]
    (boolean (:treated? (encounter s encounter-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :encounter/upsert
      (d/transact! conn [(encounter->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/encounter-id (first path) :assessment/payload (ls/enc payload)}])

      :credential/set
      (d/transact! conn [{:credential/encounter-id (first path) :credential/payload (ls/enc payload)}])

      :encounter/mark-treated
      (let [encounter-id (first path)
            {:keys [result encounter-patch]} (administer-treatment-session! s encounter-id)
            jurisdiction (:jurisdiction (encounter s encounter-id))
            next-n (inc (next-sequence s jurisdiction))]
        (d/transact! conn
                     [(encounter->tx (assoc encounter-patch :id encounter-id))
                      {:sequence/jurisdiction jurisdiction :sequence/next next-n}
                      {:session/seq (count (session-history s)) :session/record (ls/enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (ls/append-blob! conn :ledger/seq :ledger/fact (count (ledger s)) fact)
    fact)
  (with-encounters [s encounters]
    (when (seq encounters) (d/transact! conn (mapv encounter->tx (vals encounters)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:encounters ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [encounters]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-encounters s encounters))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo encounter set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
