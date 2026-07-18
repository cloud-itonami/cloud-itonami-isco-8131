(ns chemcoord.governor
  "ChemCoordGovernor — the independent safety/traceability layer named
  in this repository's README/business-model.md, gating every
  chemical-plant scheduling/logistics coordination proposal an advisor
  may make for a chemical processing plant under coordination. The
  governor never dispatches hardware itself, never operates chemical-
  processing equipment, and never allows a proposal to finalize a
  chemical-process-execution decision (authorizing a process/reaction
  to proceed), finalize a plant-safety-clearance decision, or override
  a plant safety officer's judgment — this actor coordinates CHEMICAL-
  PLANT SCHEDULING/LOGISTICS ONLY. Modeled on cloud-itonami-isco-7542's
  blastcoord.governor.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. plant provenance        — the operator/plant record must be
                                 independently verified/registered
                                 before any action.
    2. no-actuation            — proposal :effect must be :propose
                                 (the governor never dispatches
                                 hardware and never operates chemical-
                                 processing equipment; it only gates
                                 what the advisor may coordinate).
    3. closed op-allowlist     — :op must be one of the four
                                 coordination ops (:log-work-record,
                                 :schedule-crew-operation,
                                 :flag-safety-concern,
                                 :coordinate-supply-order). No op that
                                 directly finalizes a chemical-process-
                                 execution decision, finalizes a plant-
                                 safety-clearance decision, or
                                 overrides plant-safety-officer
                                 authority exists in this allowlist —
                                 these decision classes are
                                 structurally absent, not merely
                                 gated. This actor never operates
                                 chemical-processing equipment itself;
                                 process-chemical/reagent handling is
                                 entirely out of scope for this
                                 administrative-coordination actor
                                 (:coordinate-supply-order covers
                                 plant-equipment/administrative-supply
                                 procurement only, never process
                                 chemicals or reagents themselves).
    4. plant-mismatch          — if the proposal names a plant, it
                                 must be the SAME plant verified for
                                 this request (defense-in-depth against
                                 a proposal quietly targeting a
                                 different, unverified plant).
    5. operator basis          — if the proposal references an
                                 operator, that operator must be a
                                 REGISTERED certified chemical-plant
                                 operator belonging to this plant (an
                                 unregistered or foreign-plant operator
                                 reference is not a routine scheduling
                                 proposal).
    6. scope-exclusion         — a proposal that attempts to finalize
                                 a chemical-process-execution decision
                                 (authorizing a process/reaction to
                                 proceed), to finalize a plant-safety-
                                 clearance decision, or to override a
                                 plant safety officer's judgment, is a
                                 hard, PERMANENT block — never
                                 overridable by human approval,
                                 regardless of confidence or stake, and
                                 NEVER auto-commit-eligible under any
                                 confidence level. Detected as
                                 finalization/execution ACTION PHRASES
                                 (e.g. 'authorize the process to
                                 proceed', 'finalize the chemical-
                                 process operation', 'override the
                                 plant safety officer's judgment') in
                                 free-text proposal fields, never as
                                 bare domain nouns ('chemical',
                                 'reaction', 'process', 'batch',
                                 'reagent') — bare-noun matching would
                                 false-trip on the default mock
                                 advisor's own routine rationale text,
                                 since this actor's entire domain is
                                 chemical-plant scheduling coordination.
                                 See `chemcoord.governor-test`
                                 `default-mock-advisor-proposals-never-self-trip-scope-exclusion`.

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off,
  regardless of confidence):
    7. :op :flag-safety-concern always escalates (a surfaced process-
                                 anomaly, leak-risk or equipment-
                                 condition concern ALWAYS requires
                                 human review — the governor never
                                 resolves a safety concern itself, and
                                 this is unconditional — no confidence-
                                 level exception, ever).
    8. :op :coordinate-supply-order with :cost above
                                 `supply-order-cost-threshold` always
                                 escalates.
    9. low confidence (< `confidence-floor`)."
  (:require [clojure.string :as str]
            [chemcoord.store :as store]))

(def confidence-floor 0.6)

(def supply-order-cost-threshold 20000)

(def ^:private allowed-ops
  #{:log-work-record :schedule-crew-operation :flag-safety-concern
    :coordinate-supply-order})

(def ^:private always-escalate-ops #{:flag-safety-concern})

;; Scope-exclusion is matched as finalization/execution ACTION
;; PHRASES, never as bare nouns ("chemical", "reaction", "process",
;; "batch", "reagent") — this actor's entire domain is chemical-plant
;; scheduling coordination, so bare-noun matching would false-trip on
;; the default mock advisor's own routine rationale text (e.g.
;; "proposed :coordinate-supply-order for plant CP-1" naming plant
;; equipment and consumable signage, or a crew-schedule proposal
;; naming a batch/process under preparation). See governor-test's
;; dedicated self-trip guard.
(def ^:private scope-exclusion-phrases
  ["authorize the process to proceed"
   "authorize the reaction to proceed"
   "approve the process to proceed"
   "approve the reaction to proceed"
   "clear the process to proceed"
   "clear the plant for the process"
   "clear the plant for the reaction"
   "finalize the process authorization"
   "finalize the process-authorization decision"
   "finalize the reaction authorization"
   "finalize the chemical-process operation"
   "finalize the chemical process operation"
   "finalize the process execution"
   "finalize the process-execution decision"
   "finalize the plant-safety-clearance decision"
   "finalize the plant safety clearance"
   "finalize the plant-safety clearance decision"
   "execute the process directly"
   "execute the reaction directly"
   "perform the process directly"
   "perform the reaction directly"
   "initiate the reaction directly"
   "initiate the process directly"
   "trigger the reaction directly"
   "start the process directly"
   "start the reaction directly"
   "dispatch the crew to run the process"
   "dispatch the operator to start the reaction"
   "sign off the process authorization"
   "sign the process authorization"
   "issue the process authorization"
   "issue the plant-safety clearance"
   "issue the plant safety clearance"
   "override the plant safety officer's judgment"
   "override the plant safety officer"
   "bypass the plant safety officer"
   "bypass the safety clearance"
   "bypass the process authorization"])

(defn- scope-excluded-text [proposal]
  (str/lower-case (str (:rationale proposal) " " (:description proposal))))

(defn scope-exclusion-violation?
  "true if any free-text field of `proposal` contains a
  finalization/execution action phrase attempting to finalize a
  chemical-process-execution decision, finalize a plant-safety-
  clearance decision, or override plant-safety-officer authority.
  Phrased as multi-word action phrases (never bare nouns) so this
  never false-trips on legitimate chemical-plant-scheduling-
  coordination domain vocabulary."
  [proposal]
  (let [text (scope-excluded-text proposal)]
    (boolean (some #(str/includes? text %) scope-exclusion-phrases))))

(defn- hard-violations [{:keys [request proposal]} plant-record o]
  (let [{:keys [op plant-id operator-id]} proposal]
    (cond-> []
      (nil? plant-record)
      (conj {:rule :no-plant :detail "未登録 plant/chemical-processing record"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は chemical-plant 判断を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op :detail "closed op-allowlist 外の op（chemical-process-execution 決定の確定・plant-safety-clearance 決定の確定・plant safety officer の判断の上書きにあたる op は許可されていない）"})

      (and plant-id (not= plant-id (:plant-id request)))
      (conj {:rule :plant-mismatch :detail "proposal の plant が request で検証済みの plant と一致しない"})

      (and operator-id (nil? o))
      (conj {:rule :unknown-operator :detail "未登録 operator への提案は不可"})

      (and o (not= (:plant-id o) (:plant-id request)))
      (conj {:rule :operator-wrong-plant :detail "operator が別 plant 所属"})

      (scope-exclusion-violation? proposal)
      (conj {:rule :scope-exclusion-violation
             :detail "chemical-process-execution 決定の確定・plant-safety-clearance 決定の確定・plant safety officer の判断の上書きにあたる提案は恒久的に禁止（human 承認でも上書き不可）"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `chemcoord.store/Store`. Pure — never mutates
  the store, never dispatches a robot action, never operates chemical-
  processing equipment."
  [request _context proposal store]
  (let [plant-record (store/plant store (:plant-id request))
        o (some->> (:operator-id proposal) (store/operator store))
        hard (hard-violations {:request request :proposal proposal} plant-record o)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        cost (:cost proposal)
        over-threshold? (and (= :coordinate-supply-order (:op proposal))
                              (number? cost) (> cost supply-order-cost-threshold))
        always-risky? (or (contains? always-escalate-ops (:op proposal)) over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
