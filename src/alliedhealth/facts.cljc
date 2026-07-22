(ns alliedhealth.facts
  "Per-jurisdiction allied-health regulatory catalog -- the G2-style
  spec-basis table the Allied Health Governor checks every
  `:assessment/verify` proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's allied-health-
  licensing framework, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official allied-
  health-licensing authority (see `:provenance`); they are a STARTING
  catalog, not a from-scratch survey of all ~194 jurisdictions.
  Extending coverage is additive: add one map to `catalog`, cite a
  real source, done -- never invent a jurisdiction's requirements to
  make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the patient-
  consent/assessment/scope-of-practice-verification/treatment-plan
  evidence set this blueprint's own Offer names; `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any `:actuation/administer-treatment-session`
  proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省 (Ministry of Health, Labour and Welfare)"
          :legal-basis "理学療法士及び作業療法士法 (Act on Physical Therapists and Occupational Therapists) / あん摩マッサージ指圧師、はり師、きゅう師等に関する法律"
          :national-spec "理学療法士・作業療法士等の免許制度および業務範囲基準"
          :provenance "https://www.mhlw.go.jp/stf/seisakunitsuite/bunya/kenkou_iryou/iryou/other/index.html"
          :required-evidence ["患者同意記録 (patient-consent-record)"
                              "評価記録 (assessment-record)"
                              "業務範囲確認記録 (scope-of-practice-verification-record)"
                              "治療計画記録 (treatment-plan-record)"]}
   "USA" {:name "United States"
          :owner-authority "Federation of State Boards of Physical Therapy (FSBPT) / state allied-health licensing boards"
          :legal-basis "State Practice Acts for allied-health professions; Medicare allied-health provider requirements, 42 CFR Part 484"
          :national-spec "Licensed allied-health provider scope-of-practice and treatment-documentation requirements"
          :provenance "https://www.fsbpt.org/"
          :required-evidence ["Patient consent record"
                              "Assessment record"
                              "Scope-of-practice verification record"
                              "Treatment-plan record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Health and Care Professions Council (HCPC)"
          :legal-basis "Health and Social Work Professions Order 2001"
          :national-spec "Registered allied-health-professional scope-of-practice and standards of proficiency"
          :provenance "https://www.hcpc-uk.org/"
          :required-evidence ["Patient consent record"
                              "Assessment record"
                              "Scope-of-practice verification record"
                              "Treatment-plan record"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesministerium für Gesundheit (BMG)"
          :legal-basis "Gesetz über die Berufe in der Physiotherapie (MPhG) / Heilpraktikergesetz"
          :national-spec "Zulassungs- und Tätigkeitsanforderungen an Heilmittelerbringer im Bereich der nichtärztlichen Heilberufe"
          :provenance "https://www.bundesgesundheitsministerium.de/themen/pflege.html"
          :required-evidence ["Einwilligungsprotokoll (patient-consent-record)"
                              "Befundprotokoll (assessment-record)"
                              "Tätigkeitsbereichsnachweis (scope-of-practice-verification-record)"
                              "Behandlungsplanprotokoll (treatment-plan-record)"]}
   ;; India: dedicated allied-health statute distinct from India's medical
   ;; (National Medical Commission Act, 2019) / nursing regulation, verified
   ;; 2026-07 directly from the primary Act text hosted at India Code (the
   ;; Ministry of Law and Justice's official legislative repository, part of
   ;; the india.gov.in family) -- the domains ncahp.gov.in and
   ;; ncahp.mohfw.gov.in were unreachable this session (DNS failure / TCP
   ;; timeout, no bot-detection challenge involved) and have zero Internet
   ;; Archive snapshots, so the Commission's own site could NOT be used as a
   ;; primary source here -- this is disclosed, not papered over.
   ;; The Act (s. 22) obliges every State Government to constitute a State
   ;; Council within six months of commencement; whether every state has
   ;; actually done so on the ground could not be independently confirmed
   ;; from this session's reachable sources, so :national-spec below states
   ;; the statutory design (what the Act requires), not a claim that every
   ;; State Council is operationally live today.
   "IND" {:name "India"
          :owner-authority "National Commission for Allied and Healthcare Professions (NCAHP), Ministry of Health and Family Welfare, Government of India -- plus State Allied and Healthcare Councils constituted under Chapter III of the Act"
          :legal-basis "National Commission for Allied and Healthcare Professions Act, 2021 (Act No. 14 of 2021, 28th March 2021) -- s. 13 (Central Allied and Healthcare Professionals' Register, an online and live register), s. 33 (registration in State Register), s. 55 (no allied and healthcare professional shall discharge any duty or perform any function not authorised by this Act or any treatment not authorised within the scope of practice of the profession), Chapter III ss. 22-38 (State Allied and Healthcare Council)"
          :national-spec "Minimum standards of allied-and-healthcare education, curricula and scope-of-practice framed by the National Commission under s. 11, given effect in each State through the State Council's Autonomous Boards under s. 29 (including the Allied and Healthcare Professions Ethics and Registration Board, which maintains the online and live State Register)"
          :provenance "https://www.indiacode.nic.in/handle/123456789/16824"
          :required-evidence ["Patient consent record"
                              "Assessment record"
                              "Scope-of-practice verification record"
                              "Treatment-plan record"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to administer a
  treatment session on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-8690 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `alliedhealth.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
