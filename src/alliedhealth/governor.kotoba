(ns alliedhealth.governor
  "Allied Health Governor -- the independent compliance layer that
  earns the AlliedHealth-LLM the right to commit. The LLM has no
  notion of jurisdictional allied-health-licensing law, whether a
  proposed treatment actually falls within a practitioner's own
  recorded scope of practice, whether the treating practitioner's own
  credential is actually current, or when an act stops being a draft
  and becomes a real-world treatment-session administration, so this
  MUST be a separate system able to *reject* a proposal and fall back
  to HOLD -- the allied-health analog of `cloud-itonami-isic-8620`'s
  ClinicGovernor.

  Five checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them (you don't get to approve your way
  past a fabricated jurisdiction spec-basis, incomplete evidence, a
  treatment outside the practitioner's own scope of practice, a not-
  current credential, or a double treatment-session administration).
  The confidence/actuation gate is SOFT: it asks a human to look (low
  confidence / actuation), and the human may approve -- but see
  `alliedhealth.phase`: for `:stake :actuation/administer-treatment-
  session` (a real treatment-session administration) NO phase ever
  allows auto-commit either. Two independent layers agree that
  actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source (`alliedhealth.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/administer-
                                       treatment-session`, has the
                                       jurisdiction actually been
                                       assessed with a full patient-
                                       consent-record/assessment-
                                       record/scope-of-practice-
                                       verification-record/treatment-
                                       plan-record evidence checklist
                                       on file?
    3. Treatment outside scope
       of practice                    -- for `:actuation/administer-
                                       treatment-session`,
                                       INDEPENDENTLY recompute whether
                                       the encounter's own proposed
                                       treatment falls outside its own
                                       recorded practitioner scope-of-
                                       practice set (`alliedhealth.
                                       registry/treatment-outside-
                                       scope-of-practice?`) -- needs no
                                       proposal inspection at all. A
                                       GENUINELY NEW concept in this
                                       fleet, grep-verified absent from
                                       every prior sibling's check
                                       names -- the FIFTH instance of
                                       this fleet's set-membership/
                                       conflict check family
                                       (`clinic`/`veterinary`/
                                       `entertainment`/`nursing`
                                       established the first four, all
                                       'presence-in-forbidden-set'
                                       polarity), and the FIRST
                                       instance of the 'absence-from-
                                       allowed-set' polarity.
    4. Credential not current      -- reported by THIS proposal itself
                                       (a `:credential/screen` that
                                       just found a lapsed credential),
                                       or already on file for the
                                       encounter (`:credential/
                                       screen`/`:actuation/administer-
                                       treatment-session`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...(forty-
                                       three prior siblings, most
                                       recently `sports.governor/
                                       background-check-not-cleared-
                                       violations`)...established -- a
                                       LITERAL reuse of `clinic.
                                       governor/credential-not-current-
                                       violations`'s own concept
                                       (already reused ~18 times across
                                       the fleet), not claimed as new --
                                       the 44th distinct application of
                                       this exact discipline overall.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       administer-treatment-session` (a
                                       REAL clinical act) -> escalate.

  One more guard, double-administration prevention, is enforced but NOT
  listed as a numbered HARD check above because it needs no upstream
  comparison at all -- `already-treated-violations` refuses to
  administer a treatment session to the SAME encounter twice, off a
  dedicated `:treated?` fact (never a `:status` value) -- the SAME
  'check a dedicated boolean, not status' discipline every prior
  sibling governor's guards establish, informed by `cloud-itonami-
  isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [alliedhealth.facts :as facts]
            [alliedhealth.registry :as registry]
            [alliedhealth.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Administering a real treatment session is the ONE real-world
  actuation event this actor performs -- a single-member set,
  matching `cloud-itonami-isic-6511`'s/`6621`'s/`6629`'s/`6612`'s/
  `6492`'s/`7120`'s/`8620`'s single-actuation shape."
  #{:actuation/administer-treatment-session})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:assessment/verify` (or `:actuation/administer-treatment-
  session`) proposal with no spec-basis citation is a HARD violation
  -- never invent a jurisdiction's allied-health-licensing
  requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:assessment/verify :actuation/administer-treatment-session} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は業務範囲基準として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/administer-treatment-session`, the jurisdiction's
  required patient-consent-record/assessment-record/scope-of-
  practice-verification-record/treatment-plan-record evidence must
  actually be satisfied -- do not trust the advisor's self-reported
  confidence alone."
  [{:keys [op subject]} st]
  (when (= op :actuation/administer-treatment-session)
    (let [e (store/encounter st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction e) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(患者同意記録/評価記録/業務範囲確認記録/治療計画記録等)が充足していない状態での提案"}]))))

(defn- treatment-outside-scope-of-practice-violations
  "For `:actuation/administer-treatment-session`, INDEPENDENTLY
  recompute whether the encounter's own proposed treatment falls
  outside its own recorded practitioner scope-of-practice set via
  `alliedhealth.registry/treatment-outside-scope-of-practice?` --
  needs no proposal inspection at all, since its inputs are permanent
  ground-truth fields already on the encounter."
  [{:keys [op subject]} st]
  (when (= op :actuation/administer-treatment-session)
    (let [e (store/encounter st subject)]
      (when (registry/treatment-outside-scope-of-practice? e)
        [{:rule :treatment-outside-scope-of-practice
          :detail (str subject " の提案治療(" (:proposed-treatment e)
                      ")が担当者自身の業務範囲" (:practitioner-scope-of-practice e) "の範囲外")}]))))

(defn- credential-not-current-violations
  "A not-current practitioner credential -- reported by THIS proposal
  (e.g. a `:credential/screen` that itself just found a lapsed
  credential), or already on file in the store for the encounter
  (`:credential/screen`/`:actuation/administer-treatment-session`) --
  is a HARD, un-overridable hold. Evaluated UNCONDITIONALLY (not
  scoped to a specific op) so the screening op itself can HARD-hold on
  its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (true? (get-in proposal [:value :credential-not-current?]))
        encounter-id (when (contains? #{:credential/screen :actuation/administer-treatment-session} op) subject)
        hit-on-file? (and encounter-id (true? (:credential-not-current? (store/credential-of st encounter-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :credential-not-current
        :detail "担当者の資格が最新でない状態での治療実施提案は進められない"}])))

(defn- already-treated-violations
  "For `:actuation/administer-treatment-session`, refuses to
  administer a treatment session to the SAME encounter twice, off a
  dedicated `:treated?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/administer-treatment-session)
    (when (store/encounter-already-treated? st subject)
      [{:rule :already-treated
        :detail (str subject " は既に治療実施済み")}])))

(defn check
  "Censors an AlliedHealth-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (treatment-outside-scope-of-practice-violations request st)
                           (credential-not-current-violations request proposal st)
                           (already-treated-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
