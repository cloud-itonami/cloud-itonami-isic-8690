(ns alliedhealth.registry
  "Pure-function treatment-session-administration record construction
  -- an append-only allied-health book-of-record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a treatment-session
  reference number -- every practice/jurisdiction assigns its own
  reference format. This namespace does NOT invent one; it builds a
  jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `alliedhealth.facts` uses.

  `treatment-outside-scope-of-practice?` is a GENUINELY NEW check
  concept in this fleet (grep-verified absent from every prior
  sibling's check names before this claim was finalized -- no
  'scope-of-practice'/'scope-of-license'/'out-of-scope' concept
  exists anywhere else in this fleet). It reuses the set-membership/
  conflict SHAPE `clinic.registry/treatment-contraindicated?`
  established (single item vs. a set, no arithmetic comparison), but
  with the OPPOSITE polarity: `contraindicated?` checks for the
  proposed item's PRESENCE in a forbidden set (bad if present); this
  check tests for the proposed item's ABSENCE from an allowed set
  (bad if absent). The FIFTH instance of this fleet's set-membership/
  conflict family overall (`clinic`/`veterinary`/`entertainment`/
  `nursing` established the first four, all 'presence-in-forbidden-
  set' polarity), and the FIRST instance of the 'absence-from-
  allowed-set' polarity -- a direct, natural mapping onto real
  allied-health regulatory practice (each allied-health profession has
  a defined scope of practice; treating outside it is exactly the
  failure mode a practice must not let an advisor wave through).

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real allied-health-practice system. It builds the
  RECORD a practice would keep, not the act of administering the
  treatment session itself (that is `alliedhealth.operation`'s
  `:actuation/administer-treatment-session`, always human-gated -- see
  README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  practice's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn treatment-outside-scope-of-practice?
  "Does `encounter`'s own `:proposed-treatment` fall OUTSIDE its own
  recorded `:practitioner-scope-of-practice` set? A pure ground-truth
  check against the encounter's own permanent fields -- no upstream
  comparison needed. The FIFTH instance of this fleet's set-
  membership/conflict check family, and the FIRST 'absence-from-
  allowed-set' polarity instance (see ns docstring)."
  [{:keys [proposed-treatment practitioner-scope-of-practice]}]
  (not (contains? (set practitioner-scope-of-practice) proposed-treatment)))

(defn register-treatment-session
  "Validate + construct the TREATMENT-SESSION registration DRAFT --
  the allied-health practice's own act of administering a real
  treatment session. Pure function -- does not touch any real allied-
  health-practice system; it builds the RECORD a practice would keep.
  `alliedhealth.governor` independently re-verifies the encounter's own
  scope-of-practice ground truth, and blocks a double-administration
  for the same encounter, before this is ever allowed to commit."
  [encounter-id jurisdiction sequence]
  (when-not (and encounter-id (not= encounter-id ""))
    (throw (ex-info "treatment-session: encounter_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "treatment-session: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "treatment-session: sequence must be >= 0" {})))
  (let [session-number (str (str/upper-case jurisdiction) "-TX-" (zero-pad sequence 6))
        record {"record_id" session-number
                "kind" "treatment-session-draft"
                "encounter_id" encounter-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "session_number" session-number
     "certificate" (unsigned-certificate "TreatmentSession" session-number session-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
