(ns chemcoord.advisor
  "Chemical-Plant Scheduling & Logistics Coordination Advisor — the
  advisor named in this repository's README, proposing a chemical-
  plant scheduling/logistics coordination operation (log a production-
  run/batch/progress record, schedule a crew/shift operation, flag a
  safety concern, coordinate a supply order) from a plant roster,
  operator roster and plant schedule. Swappable mock/llm; the advisor
  ONLY proposes — `chemcoord.governor` checks plant/operator
  registration, the closed op-allowlist and scope-exclusion
  independently, and always escalates safety-concern flags, above-
  threshold supply orders and low-confidence proposals. This actor
  coordinates CHEMICAL-PLANT SCHEDULING/LOGISTICS ONLY — it never
  operates chemical-processing equipment itself and never proposes to
  finalize a chemical-process-execution decision (authorizing a
  process/reaction to proceed), finalize a plant-safety-clearance
  decision, or override a plant safety officer's judgment. Modeled on
  cloud-itonami-isco-7542's blastcoord.advisor.

  A proposal: {:op :log-work-record|:schedule-crew-operation|
               :flag-safety-concern|:coordinate-supply-order
               :effect :propose :plant-id str :operator-id str? :cost
               number? :stake kw :confidence n :rationale str}"
  (:require [clojure.edn :as edn]))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake plant-id operator-id cost task materials
                             concern-type severity description time-window
                             progress-notes batch-reference]}]
  (cond-> {:op op
           :effect :propose
           :plant-id plant-id
           :stake (or stake :low)
           :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
           :rationale (str "proposed " (name op) " for plant " plant-id)}
    operator-id (assoc :operator-id operator-id)
    (some? cost) (assoc :cost cost)
    task (assoc :task task)
    materials (assoc :materials materials)
    concern-type (assoc :concern-type concern-type)
    severity (assoc :severity severity)
    description (assoc :description description)
    time-window (assoc :time-window time-window)
    progress-notes (assoc :progress-notes progress-notes)
    batch-reference (assoc :batch-reference batch-reference)))

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a chemical-products-plant-and-machine-operators chemical-
   plant scheduling/logistics coordination advisor. Given a request,
   propose an :op (:log-work-record, :schedule-crew-operation,
   :flag-safety-concern or :coordinate-supply-order ONLY — no other op
   exists), the :plant-id, an honest :confidence and a :stake. You
   coordinate chemical-plant scheduling and logistics ONLY: never
   propose to finalize a chemical-process-execution decision
   (authorizing a process/reaction to proceed), never propose to
   finalize a plant-safety-clearance decision, never propose to
   override a plant safety officer's judgment, and never propose an op
   outside the closed allowlist above. You never operate chemical-
   processing equipment yourself: :coordinate-supply-order covers
   plant-equipment/administrative-supply procurement only, never
   process chemicals or reagents themselves. The governor checks
   plant/operator registration and scope independently. Safety-concern
   flags (process anomaly, leak risk, equipment condition) and above-
   threshold supply orders always require human sign-off regardless of
   confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
