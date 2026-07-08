(ns alliedhealth.alliedhealthadvisor
  "AlliedHealth-LLM client -- the *contained intelligence node* for the
  allied-health actor.

  It normalizes encounter intake, drafts a per-jurisdiction allied-
  health evidence checklist, screens encounters for a lapsed
  practitioner credential, and drafts the treatment-session-
  administration action. CRITICAL: it is a smart-but-untrusted
  advisor. It returns a *proposal* (with a rationale + the fields it
  cited), never a committed record or a real treatment-session
  administration. Every output is censored downstream by
  `alliedhealth.governor` before anything touches the SSoT, and
  `:actuation/administer-treatment-session` proposals NEVER auto-
  commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/administer-treatment-session | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [alliedhealth.facts :as facts]
            [alliedhealth.registry :as registry]
            [alliedhealth.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the patient, proposed treatment or jurisdiction.
  High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "診療記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :encounter/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-assessment
  "Per-jurisdiction allied-health evidence checklist draft. `:no-
  spec?` injects the failure mode we must defend against: proposing a
  checklist for a jurisdiction with NO official spec-basis in
  `alliedhealth.facts` -- the Allied Health Governor must reject this
  (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [e (store/encounter db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction e))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "alliedhealth.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-credential
  "Practitioner-credential screening draft. `:credential-not-
  current?` on the encounter record injects the failure mode: the
  Allied Health Governor must HOLD, un-overridably, on any lapsed
  credential."
  [db {:keys [subject]}]
  (let [e (store/encounter db subject)]
    (cond
      (nil? e)
      {:summary "対象encounterが見つかりません" :rationale "no encounter record"
       :cites [] :effect :credential/set :value {:encounter-id subject :credential-not-current? nil}
       :stake nil :confidence 0.0}

      (true? (:credential-not-current? e))
      {:summary    (str (:patient-name e) ": 担当者の資格失効を検出")
       :rationale  "スクリーニングが資格失効を検出。人手確認とホールドが必須。"
       :cites      [:credential-check]
       :effect     :credential/set
       :value      {:encounter-id subject :credential-not-current? true}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:patient-name e) ": 資格は有効")
       :rationale  "資格スクリーニング完了。"
       :cites      [:credential-check]
       :effect     :credential/set
       :value      {:encounter-id subject :credential-not-current? false}
       :stake      nil
       :confidence 0.9})))

(defn- propose-treatment-session
  "Draft the actual TREATMENT-SESSION action -- administering a real
  treatment session. ALWAYS `:stake :actuation/administer-treatment-
  session` -- this is a REAL-WORLD act (a patient's health outcome
  depends on it), never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`alliedhealth.phase`); the governor also always escalates on
  `:actuation/administer-treatment-session`. Two independent layers
  agree, deliberately."
  [db {:keys [subject]}]
  (let [e (store/encounter db subject)
        safe? (and e (not (registry/treatment-outside-scope-of-practice? e))
                   (not (:credential-not-current? e)))]
    {:summary    (str subject " 向け治療実施提案"
                      (when e (str " (patient=" (:patient-name e) ")")))
     :rationale  (if e
                   (str "proposed-treatment=" (:proposed-treatment e)
                        " practitioner-scope-of-practice=" (:practitioner-scope-of-practice e))
                   "encounterが見つかりません")
     :cites      (if e [subject] [])
     :effect     :encounter/mark-treated
     :value      {:encounter-id subject}
     :stake      :actuation/administer-treatment-session
     :confidence (if safe? 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :encounter/intake                        (normalize-intake db request)
    :assessment/verify                       (verify-assessment db request)
    :credential/screen                       (screen-credential db request)
    :actuation/administer-treatment-session  (propose-treatment-session db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは他の人体健康活動事業(理学療法・鍼灸・パラメディカル等)の"
       "治療実施エージェントの助言者です。与えられた事実のみに基づき、提案を1つだけ"
       "EDNマップで返します。説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:encounter/upsert|:assessment/set|:credential/set|"
       ":encounter/mark-treated) "
       ":stake(:actuation/administer-treatment-session か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :assessment/verify                       {:encounter (store/encounter st subject)}
    :credential/screen                       {:encounter (store/encounter st subject)}
    :actuation/administer-treatment-session  {:encounter (store/encounter st subject)}
    {:encounter (store/encounter st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Allied Health Governor
  escalates/holds -- an LLM hiccup can never auto-administer a
  treatment session."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :alliedhealthadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
