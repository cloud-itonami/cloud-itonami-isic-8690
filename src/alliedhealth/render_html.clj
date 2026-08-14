(ns alliedhealth.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for `cloud-itonami-isic-8690`: this
  repo previously had NO sample console and no generator at all (only
  the product LP at `docs/index.html`). This namespace drives the REAL
  actor stack -- `alliedhealth.operation` (a langgraph-clj StateGraph)
  -> `alliedhealth.governor` -> `alliedhealth.store` -- via
  `langgraph.graph/run*`, exactly the way `alliedhealth.sim`
  (`clojure -M:dev:run`) does, and renders the resulting store.

  NOTHING on the page is hand-typed. Every encounter id, patient name,
  jurisdiction, treatment, scope-of-practice set, session number,
  violation detail string, rollout-phase op set and spec-basis citation
  is read back out of `alliedhealth.store`, `alliedhealth.phase` or
  `alliedhealth.facts` after the run. The seed set is
  `alliedhealth.store/demo-data` (`encounter-1`..`encounter-4`) -- the
  same ids this repo's own `sim` driver uses, verified by running
  `clojure -M:dev:run` BEFORE this file was written.

  What the scenario proves, beyond `sim`: `sim` exercises FOUR of the
  Allied Health Governor's five HARD rules. This scenario exercises
  ALL FIVE, one hold per rule, by additionally driving an
  `:actuation/administer-treatment-session` against `encounter-2` --
  whose jurisdiction assessment HARD-held moments earlier, so no
  assessment is on file and `evidence-incomplete` fires alone. That
  full coverage is a BUILD-TIME INVARIANT, not a convention: `-main`
  throws unless the observed hold-rule set equals `expected-hard-rules`
  (see below), so a governor rule that silently stops firing fails the
  build instead of quietly vanishing from the page.

  Deterministic by construction: no timestamp, no random, no wall
  clock, no map-iteration order in the output (every set is `sort`ed
  and every catalog is walked over `sort`ed keys). Two consecutive
  runs are byte-identical.

  Styling: the DADS (デジタル庁デザインシステム) primitives below are
  EXTRACTED FROM THIS REPO'S OWN `docs/index.html`, which already
  vendors the design system. No `jp-go-dds` dependency is added -- that
  would make the build network-dependent, and the pinned
  `tokens/bridge-css` bridges a different token vocabulary than the one
  this console uses. Reading the values out of the local vendored copy
  keeps the build offline AND keeps the console on the same face as the
  product LP.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [alliedhealth.facts :as facts]
            [alliedhealth.operation :as op]
            [alliedhealth.phase :as phase]
            [alliedhealth.store :as store]
            [langgraph.graph :as g]))

(def ^:private operator
  "The human operator context every run is executed under. `:phase` is
  read from `alliedhealth.phase/default-phase` rather than typed, so the
  console can never claim a rollout phase the code doesn't actually run."
  {:actor-id "op-1" :actor-role :licensed-professional :phase phase/default-phase})

(def expected-hard-rules
  "Every HARD rule `alliedhealth.governor` can emit -- the five numbered
  checks in its docstring plus the double-administration guard. The
  build FAILS unless the scenario exercises all of them (see `-main`).
  This is the build-time invariant that keeps the console honest: a
  governor rule that stops firing, or a scenario edit that stops
  reaching one, breaks the build rather than silently shrinking the
  evidence on the page."
  #{:no-spec-basis
    :evidence-incomplete
    :treatment-outside-scope-of-practice
    :credential-not-current
    :already-treated})

;; ----------------------------- the real run -----------------------------

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by (:actor-id operator)}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario reaching every
  disposition this actor can produce, and returns
  `{:db store :runs [{:thread .. :request .. :final <graph result>}]}`.

  Only the FINAL result of each thread is kept: langgraph's `:audit`
  channel uses an `into` reducer, so a resumed run's state already
  contains the pre-interrupt facts and keeping both would double-count.

  Committed path (`encounter-1`, JPN, `:manual-therapy` which IS in the
  practitioner's recorded scope, credential current):
    intake              -- phase-3 auto-commit, governor-clean, no actuation stake
    assessment/verify   -- escalates (`:phase-approval`), human approves, commits
    credential/screen   -- escalates, human approves, commits
    actuation/administer-treatment-session
                        -- ALWAYS escalates (`:actuation`); no phase ever
                           auto-commits it; human approves, commits, and the
                           treatment-session draft record is minted

  Five HARD holds, one per rule, none of which ever reaches a human:
    already-treated                     -- `encounter-1` treated a SECOND time
    no-spec-basis                       -- `encounter-2` assessed for `ATL`,
                                           deliberately absent from `facts/catalog`
    evidence-incomplete                 -- `encounter-2` treatment attempted with
                                           no assessment on file (its assessment
                                           HARD-held above, so nothing committed)
    treatment-outside-scope-of-practice -- `encounter-3` proposes
                                           `:spinal-manipulation`, absent from its
                                           own recorded scope set, AFTER its
                                           assessment was approved (so evidence is
                                           complete and this rule fires alone)
    credential-not-current              -- `encounter-4` screening finds a lapsed
                                           practitioner credential"
  []
  (let [db (store/seed-db)
        actor (op/build db)
        runs (atom [])
        step! (fn step! [tid request approve?]
                (let [r (if approve?
                          (do (exec! actor tid request) (approve! actor tid))
                          (exec! actor tid request))]
                  (swap! runs conj {:thread tid :request request :final r})
                  r))]

    ;; Directory intake for every seeded encounter -- the patch is read
    ;; back out of the seed, never typed here.
    (doseq [e (store/all-encounters db)]
      (step! (str "intake-" (:id e))
             {:op :encounter/intake :subject (:id e)
              :patch {:id (:id e) :patient-name (:patient-name e)}}
             false))

    ;; encounter-1 -- the full clean lifecycle through a real treatment session.
    (step! "e1-assess"     {:op :assessment/verify :subject "encounter-1"} true)
    (step! "e1-credential" {:op :credential/screen :subject "encounter-1"} true)
    (step! "e1-treat"      {:op :actuation/administer-treatment-session
                            :subject "encounter-1"} true)

    ;; HARD -- :already-treated
    (step! "e1-treat-again" {:op :actuation/administer-treatment-session
                             :subject "encounter-1"} false)

    ;; HARD -- :no-spec-basis   (ATL is not in alliedhealth.facts/catalog)
    (step! "e2-assess" {:op :assessment/verify :subject "encounter-2"
                        :no-spec? true} false)

    ;; HARD -- :evidence-incomplete   (that assessment held, so nothing is on file)
    (step! "e2-treat" {:op :actuation/administer-treatment-session
                       :subject "encounter-2"} false)

    ;; encounter-3 -- assessment approved first, so the ONLY thing left to
    ;; catch the treatment is the scope-of-practice recompute.
    (step! "e3-assess" {:op :assessment/verify :subject "encounter-3"} true)

    ;; HARD -- :treatment-outside-scope-of-practice
    (step! "e3-treat" {:op :actuation/administer-treatment-session
                       :subject "encounter-3"} false)

    ;; HARD -- :credential-not-current
    (step! "e4-credential" {:op :credential/screen :subject "encounter-4"} false)

    {:db db :runs @runs}))

;; ----------------------------- rendering helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- kw-str
  "Render a keyword with its namespace, deterministically."
  [k]
  (if (keyword? k) (subs (str k) 1) (str k)))

(defn- sorted-names
  "Deterministic rendering of a set of keywords -- sets have no stable
  print order, so every set on the page goes through here."
  [s]
  (str/join ", " (sort (map kw-str s))))

(defn- pill [class label]
  (str "<span class=\"pill " class "\">" (esc label) "</span>"))

(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- row [& cells] (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>"
       (str/join (map #(str "<th>" (esc %) "</th>") headers))
       "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn- section [title lede body]
  (str "  <section class=\"card\">\n"
       "    <h2>" (esc title) "</h2>\n"
       "    <p class=\"muted\">" lede "</p>\n"
       body
       "  </section>\n"))

;; ----------------------------- derived views -----------------------------

(defn- audit-facts
  "Every audit fact the graph produced, in run order. `:approval-granted`
  and `:approval-requested` live ONLY here -- `alliedhealth.operation`
  appends just `:committed` and hold facts to the store ledger -- so any
  approver attribution has to be joined from this side."
  [runs]
  (vec (mapcat #(get-in % [:final :state :audit]) runs)))

(defn- holds [ledger] (filterv #(= :governor-hold (:t %)) ledger))

(defn- last-fact-for [ledger id]
  (last (filter #(= (:subject %) id) ledger)))

(defn- status-cell [ledger id]
  (let [f (last-fact-for ledger id)]
    (case (:t f)
      :committed        (pill "ok" "committed")
      :governor-hold    (pill "critical" (str "HARD hold · " (kw-str (first (:basis f)))))
      :approval-rejected (pill "critical" "approval rejected")
      (pill "muted" "no activity"))))

(defn- encounter-row [ledger e]
  (let [{:keys [id patient-name jurisdiction proposed-treatment
                practitioner-scope-of-practice credential-not-current?
                treated? session-number]} e]
    (row (code id)
         (esc patient-name)
         (str (esc jurisdiction)
              (if (facts/spec-basis jurisdiction)
                ""
                (str " " (pill "critical" "no spec-basis"))))
         (code (kw-str proposed-treatment))
         (esc (sorted-names practitioner-scope-of-practice))
         (if credential-not-current?
           (pill "critical" "not current")
           (pill "ok" "current"))
         (if treated?
           (str (pill "ok" "treated") " " (code session-number))
           (pill "muted" "not treated"))
         (status-cell ledger id))))

(defn- phase-row [[n {:keys [label writes auto]}]]
  (row (code n)
       (esc label)
       (if (seq writes) (esc (sorted-names writes)) (pill "muted" "none"))
       (if (seq auto) (esc (sorted-names auto)) (pill "muted" "none"))))

(defn- rule-row
  "One row per HARD rule the governor can emit, joined against the holds
  this run actually produced. The `detail` string is the governor's own
  message, not a paraphrase."
  [hs rule]
  (let [hit (first (filter #(some #{rule} (:basis %)) hs))
        v   (first (filter #(= rule (:rule %)) (:violations hit)))]
    (row (code (kw-str rule))
         (if hit (pill "critical" "HARD hold") (pill "muted" "not exercised"))
         (if hit (code (:subject hit)) "—")
         (if hit (code (kw-str (:op hit))) "—")
         (if v (esc (:detail v)) "—"))))

(defn- jurisdiction-row [iso3]
  (let [{:keys [name owner-authority legal-basis provenance required-evidence]}
        (facts/spec-basis iso3)]
    (row (code iso3)
         (esc name)
         (esc owner-authority)
         (esc legal-basis)
         (str (count required-evidence))
         (str "<a href=\"" (esc provenance) "\">" (esc provenance) "</a>"))))

(defn- approval-register
  "Where an approved op's commit actually lands in the SSoT, and whether
  the approver survived into that record.

  DERIVED at render time -- the presence of `:approved-by` is TESTED on
  the live register rather than asserted, so if `alliedhealth.store` is
  later changed this page corrects itself instead of repeating a stale
  claim."
  [db op subject]
  (case op
    :assessment/verify
    {:register "store/assessment-of" :record (store/assessment-of db subject)}
    :credential/screen
    {:register "store/credential-of" :record (store/credential-of db subject)}
    :actuation/administer-treatment-session
    {:register "store/encounter" :record (store/encounter db subject)}
    {:register "—" :record nil}))

(defn- approval-row [db {:keys [op subject by]}]
  (let [{:keys [register record]} (approval-register db op subject)
        kept? (contains? record :approved-by)]
    (row (code (kw-str op))
         (code subject)
         (code by)
         (code register)
         (if kept?
           (str (pill "ok" "in commit record") " " (code (:approved-by record)))
           (str (pill "warn" "audit only") " — not in commit record")))))

(defn- ledger-row [{:keys [t op subject basis summary]}]
  (row (case t
         :committed     (pill "ok" "committed")
         :governor-hold (pill "critical" "governor-hold")
         (pill "muted" (kw-str t)))
       (code (kw-str op))
       (code subject)
       (if (seq basis)
         (esc (str/join ", " (map #(if (keyword? %) (kw-str %) (str %)) basis)))
         "—")
       (esc (or summary "—"))))

(defn- session-row [r]
  (row (code (get r "record_id"))
       (code (get r "encounter_id"))
       (esc (get r "jurisdiction"))
       (esc (get r "kind"))
       (if (get r "immutable") (pill "ok" "immutable") (pill "warn" "mutable"))))

;; ----------------------------- CSS -----------------------------

(def ^:private dds-css
  "DADS primitives extracted from this repo's own vendored copy in
  `docs/index.html` (the product LP), so the console wears the same face
  as the product without adding a network dependency.

  NOTE, measured: `--color-semantic-error-1` and `-2` are BOTH dark
  (red-800 / red-900) -- they are a pair of dark steps, NOT a
  strong/weak pair. Tint backgrounds therefore use the primitive `-50`
  steps, never `-2`."
  (str
   ":root{\n"
   "  --color-neutral-white:#ffffff;\n"
   "  --color-neutral-solid-gray-50:#f2f2f2;\n"
   "  --color-neutral-solid-gray-100:#e6e6e6;\n"
   "  --color-neutral-solid-gray-200:#cccccc;\n"
   "  --color-neutral-solid-gray-536:#767676;\n"
   "  --color-neutral-solid-gray-600:#666666;\n"
   "  --color-neutral-solid-gray-700:#4d4d4d;\n"
   "  --color-neutral-solid-gray-800:#333333;\n"
   "  --color-neutral-solid-gray-900:#1a1a1a;\n"
   "  --color-primitive-blue-50:#e8f1fe;\n"
   "  --color-primitive-blue-100:#d9e6ff;\n"
   "  --color-primitive-blue-600:#3460fb;\n"
   "  --color-primitive-blue-800:#0031d8;\n"
   "  --color-primitive-blue-900:#0017c1;\n"
   "  --color-primitive-red-50:#fdeeee;\n"
   "  --color-primitive-red-200:#ffbbbb;\n"
   "  --color-primitive-red-800:#ec0000;\n"
   "  --color-primitive-red-900:#ce0000;\n"
   "  --color-primitive-green-50:#e6f5ec;\n"
   "  --color-primitive-green-200:#9bd4b5;\n"
   "  --color-primitive-green-600:#259d63;\n"
   "  --color-primitive-green-800:#197a4b;\n"
   "  --color-primitive-orange-50:#ffeee2;\n"
   "  --color-primitive-orange-200:#ffc199;\n"
   "  --color-primitive-orange-600:#fb5b01;\n"
   "  --color-primitive-orange-800:#c74700;\n"
   "  --font-family-sans:\"Noto Sans JP\",-apple-system,BlinkMacSystemFont,sans-serif;\n"
   "  --font-family-mono:\"Noto Sans Mono\",monospace;\n"
   "  --elevation-1:0 2px 8px 1px rgba(0,0,0,0.1),0 1px 5px 0 rgba(0,0,0,0.3);\n"
   "  --color-key-50:var(--color-primitive-blue-50);\n"
   "  --color-key-100:var(--color-primitive-blue-100);\n"
   "  --color-key-600:var(--color-primitive-blue-600);\n"
   "  --color-key-800:var(--color-primitive-blue-800);\n"
   "  --color-key-900:var(--color-primitive-blue-900);\n"
   "  --color-semantic-success-1:var(--color-primitive-green-600);\n"
   "  --color-semantic-success-2:var(--color-primitive-green-800);\n"
   "  --color-semantic-error-1:var(--color-primitive-red-800);\n"
   "  --color-semantic-error-2:var(--color-primitive-red-900);\n"
   "  --color-semantic-warning-orange-1:var(--color-primitive-orange-600);\n"
   "  --color-semantic-warning-orange-2:var(--color-primitive-orange-800);\n"
   "}\n"
   "*{box-sizing:border-box;}\n"
   "html{font-family:var(--font-family-sans);}\n"
   "body{margin:0;background:var(--color-neutral-solid-gray-50);"
   "color:var(--color-neutral-solid-gray-900);line-height:1.7;"
   "font-family:var(--font-family-sans);}\n"
   "header.bar{background:var(--color-key-900);color:var(--color-neutral-white);"
   "padding:24px 32px;}\n"
   "header.bar h1{margin:0 0 8px;font-size:1.35rem;line-height:1.5;}\n"
   "header.bar .badge{display:inline-block;background:var(--color-key-800);"
   "border:1px solid var(--color-primitive-blue-600);border-radius:4px;"
   "padding:4px 10px;font-size:.8rem;}\n"
   "main{max-width:1200px;margin:0 auto;padding:24px 16px 64px;}\n"
   "section.card{background:var(--color-neutral-white);"
   "border:1px solid var(--color-neutral-solid-gray-200);border-radius:8px;"
   "box-shadow:var(--elevation-1);padding:20px 24px;margin:0 0 24px;}\n"
   "section.card h2{margin:0 0 4px;font-size:1.1rem;"
   "color:var(--color-key-900);border-bottom:2px solid var(--color-key-100);"
   "padding-bottom:8px;}\n"
   "p.muted{color:var(--color-neutral-solid-gray-600);font-size:.85rem;margin:8px 0 16px;}\n"
   "table{border-collapse:collapse;width:100%;font-size:.82rem;}\n"
   "th,td{border-bottom:1px solid var(--color-neutral-solid-gray-100);"
   "padding:8px 10px;text-align:left;vertical-align:top;}\n"
   "th{background:var(--color-key-50);color:var(--color-key-900);"
   "font-weight:700;white-space:nowrap;border-bottom:2px solid var(--color-key-100);}\n"
   "tbody tr:hover{background:var(--color-neutral-solid-gray-50);}\n"
   "code{font-family:var(--font-family-mono);font-size:.9em;"
   "background:var(--color-neutral-solid-gray-50);"
   "border:1px solid var(--color-neutral-solid-gray-100);"
   "border-radius:3px;padding:1px 5px;}\n"
   "a{color:var(--color-key-800);}\n"
   ".pill{display:inline-block;border-radius:999px;padding:2px 10px;"
   "font-size:.75rem;font-weight:700;white-space:nowrap;border:1px solid transparent;}\n"
   ".pill.ok{background:var(--color-primitive-green-50);"
   "color:var(--color-semantic-success-2);"
   "border-color:var(--color-primitive-green-200);}\n"
   ".pill.critical{background:var(--color-primitive-red-50);"
   "color:var(--color-semantic-error-1);"
   "border-color:var(--color-primitive-red-200);}\n"
   ".pill.warn{background:var(--color-primitive-orange-50);"
   "color:var(--color-semantic-warning-orange-2);"
   "border-color:var(--color-primitive-orange-200);}\n"
   ".pill.muted{background:var(--color-neutral-solid-gray-50);"
   "color:var(--color-neutral-solid-gray-536);"
   "border-color:var(--color-neutral-solid-gray-200);}\n"
   "footer{max-width:1200px;margin:0 auto;padding:0 16px 48px;"
   "color:var(--color-neutral-solid-gray-600);font-size:.78rem;}\n"
   "footer code{background:var(--color-neutral-white);}\n"))

;; ----------------------------- document -----------------------------

(defn render
  "Renders the operator console from a completed `run-demo!` result.
  Reads ONLY from the real store / phase / facts namespaces."
  [{:keys [db runs]}]
  (let [ledger      (vec (store/ledger db))
        hs          (holds ledger)
        audit       (audit-facts runs)
        approvals   (filterv #(= :approval-granted (:t %)) audit)
        encounters  (store/all-encounters db)
        sessions    (store/session-history db)
        jurisdictions (sort (distinct (map :jurisdiction encounters)))
        cov         (facts/coverage jurisdictions)
        commits     (filterv #(= :committed (:t %)) ledger)]
    (str
     "<!DOCTYPE html>\n"
     "<html lang=\"ja\"><head><meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
     "<title>cloud-itonami-isic-8690 · Allied Health Operator Console</title>\n"
     "<style>\n" dds-css "</style></head>\n<body>\n"

     "<header class=\"bar\">\n"
     "  <h1>その他の人体健康活動 (ISIC 8690) — Allied Health Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · "
     "treatment-session administration is ALWAYS a human call at every phase</span>\n"
     "</header>\n"
     "<main>\n"

     (section
      "Encounters"
      (str "Live snapshot of <code>alliedhealth.store</code> after the run. "
           "Seeded from <code>alliedhealth.store/demo-data</code> — "
           (count encounters) " encounters, "
           (count commits) " committed ops, "
           (count hs) " HARD holds, "
           (count sessions) " treatment-session record(s) minted.")
      (table ["Encounter" "Patient" "Jurisdiction" "Proposed treatment"
              "Practitioner scope of practice" "Credential" "Session" "Last op"]
             (mapv #(encounter-row ledger %) encounters)))

     (section
      "Allied Health Governor — HARD rule coverage (this run)"
      (str "All five HARD rules are un-overridable: a human approver cannot approve "
           "past them, and none of these holds ever reached a human. Each row is "
           "joined against the holds this run actually produced; the reason text is "
           "the governor's own <code>:detail</code> string. "
           "<strong>Build-time invariant:</strong> <code>-main</code> throws unless "
           "every rule below is exercised, so a rule that stops firing fails the "
           "build instead of quietly disappearing from this page.")
      (table ["Rule" "This run" "Encounter" "Op" "Governor detail"]
             (mapv #(rule-row hs %) (sort expected-hard-rules))))

     (section
      "Rollout phase gate"
      (str "Rendered directly from <code>alliedhealth.phase/phases</code>. "
           "This run executed at phase <code>" (esc (:phase operator)) "</code> ("
           (esc (:label (get phase/phases (:phase operator)))) "). Note that "
           "<code>:actuation/administer-treatment-session</code> appears in no "
           "phase's auto set — including phase 3. That is a permanent structural "
           "fact, not a milestone still to come, and the governor's high-stakes "
           "gate enforces the same invariant independently.")
      (table ["Phase" "Label" "May write" "May auto-commit when governor-clean"]
             (mapv phase-row (sort-by key phase/phases))))

     (section
      "Jurisdiction spec-basis catalog"
      (str "From <code>alliedhealth.facts/catalog</code>. A jurisdiction absent "
           "from this table has NO spec-basis, full stop — the advisor must not "
           "fabricate one and the governor holds if it tries. Honest coverage over "
           "the jurisdictions actually present in this run: <strong>"
           (esc (:covered cov)) " of " (esc (:requested cov))
           "</strong> covered; missing: "
           (if (seq (:missing-jurisdictions cov))
             (str/join ", " (map code (:missing-jurisdictions cov)))
             "none")
           ".")
      (table ["ISO3" "Name" "Owner authority" "Legal basis" "Required evidence" "Provenance"]
             (mapv jurisdiction-row (sort (keys facts/catalog)))))

     (section
      "Approver attribution (measured, not assumed)"
      (str "Which approved commits actually kept the approver in the SSoT. "
           "This is <strong>derived at render time</strong> by reading each "
           "register back and testing for the <code>:approved-by</code> key — "
           "not hardcoded — so the page self-corrects if "
           "<code>alliedhealth.store</code> is later changed. "
           "Measured on this run: <code>:assessment/set</code> and "
           "<code>:credential/set</code> persist the approver because "
           "<code>commit-record!</code> stores the <code>:payload</code>, which "
           "<code>alliedhealth.operation</code>'s approval node enriches with "
           "<code>:approved-by</code>. The <code>:encounter/mark-treated</code> "
           "branch consumes neither <code>:value</code> nor <code>:payload</code> "
           "— it writes only <code>:treated?</code> and <code>:session-number</code> "
           "— so the approver of the one real-world clinical act survives in the "
           "audit trail only. Shown explicitly rather than omitted: "
           "&quot;audit only&quot; means an approver exists and is named, not that "
           "nobody approved.")
      (table ["Op" "Encounter" "Approved by" "SSoT register" "Approver retained?"]
             (mapv #(approval-row db %) approvals)))

     (section
      "Audit ledger (this run)"
      (str "Append-only decision-fact log straight out of "
           "<code>alliedhealth.store/ledger</code> — every commit and every hold, "
           "in order. " (esc (count ledger)) " facts. Approval-request and "
           "approval-grant events are not ledger facts in this actor; they live in "
           "the graph's <code>:audit</code> channel and are joined into the "
           "attribution section above.")
      (table ["Fact" "Op" "Encounter" "Basis / rules" "Summary"]
             (mapv ledger-row ledger)))

     (section
      "Treatment-session records (drafts)"
      (str "Minted by <code>alliedhealth.registry/register-treatment-session</code> "
           "on commit of the one actuation op that cleared. Every certificate this "
           "actor produces is UNSIGNED — signature is the practice's own act, not "
           "this actor's. The sequence is jurisdiction-scoped.")
      (table ["Record id" "Encounter" "Jurisdiction" "Kind" "Immutability"]
             (mapv session-row sessions)))

     "</main>\n"
     "<footer>\n"
     "  Generated at build time by <code>alliedhealth.render-html</code> "
     "(<code>clojure -M:dev:render-html</code>) by driving the real "
     "<code>alliedhealth.operation</code> langgraph StateGraph. "
     "Deterministic — no timestamps, no randomness; two consecutive runs are "
     "byte-identical. Design tokens are DADS primitives extracted from this repo's "
     "own vendored copy in <code>docs/index.html</code>.\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db] :as result} (run-demo!)
        ledger (vec (store/ledger db))
        hs (holds ledger)
        observed (into (sorted-set) (mapcat :basis hs))]

    ;; ---- build-time invariants: a console with no evidence is not a console ----
    (when (empty? hs)
      (throw (ex-info
              (str "render-html: the scenario produced ZERO :governor-hold records. "
                   "A console that shows no HARD hold is not evidence that the "
                   "Allied Health Governor works -- refusing to write it.")
              {:ledger-facts (count ledger) :holds 0})))
    (when (not= (set observed) expected-hard-rules)
      (throw (ex-info
              (str "render-html: HARD-hold rule coverage drifted. Every rule in "
                   "`expected-hard-rules` must be exercised by the scenario.")
              {:observed (vec observed)
               :expected (vec (sort expected-hard-rules))
               :missing (vec (sort (remove (set observed) expected-hard-rules)))
               :unexpected (vec (remove expected-hard-rules observed))})))
    (when (empty? (filter #(= :committed (:t %)) ledger))
      (throw (ex-info "render-html: the scenario produced ZERO commits."
                      {:ledger-facts (count ledger)})))

    (let [f (java.io.File. ^String out)]
      (when-let [p (.getParentFile f)] (.mkdirs p))
      (spit f (render result)))
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, "
                  (count hs) " HARD holds over " (count observed) " distinct rules: "
                  (str/join ", " (map kw-str observed)) ", "
                  (count (store/session-history db)) " treatment-session record(s))"))))
