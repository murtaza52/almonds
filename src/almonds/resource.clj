(ns almonds.resource
  (:require [slingshot.slingshot :refer [throw+]]
            [clojure.pprint :refer [pprint]]
            [cheshire.core :refer [generate-string]]
            [camel-snake-kebab.core :as kebab :refer [->CamelCase]]
            [clojure.data.json :refer [write-str]]
            [amazonica.aws.ec2 :as aws-ec2]
            [clojure.set :refer [difference intersection]]
            [clojure.data :as data]))

;; (defmulti resource-factory :type)
;; (defmethod resource-factory :default [_]
;;   (throw+ {:operation "Trying to create a new resource using resource factory"
;;            :msg "Either :type was not given or is an incorrect value."} ))

(defmulti create :almonds-type)
(defmulti sanitize :almonds-type)
(defmulti retrieve-raw-all :almonds-type)
(defmulti validate :almonds-type)
(defmulti aws-id :almonds-type)
(defmulti delete :almonds-type)


;; (defprotocol Resource
;;   "Defines the various operations that can be performed on a resource"
;;   (create [resource] "Create the resource")
;;   (retrieve [resource] "Returns a record based on the data returned by the provider.")
;;   (sanitize [resource] "Returns the sanitized json")
;;   (delete [resource] "Delete the resource")
;;   (update [resource] "Updates the resource")
;;   (id [resource] "Uniquely identifies a resource")
;;   (delete? [resource] "Determines if is safe to delete the resource")
;;   (update? [resource] "Can the resource be updated")
;;   (validate [resource] "Validates the resource definition")
;;   (dependents [resource] "Returns a list of child resources")
;;   (retrieve-raw [resource] "Returns the raw data retrieved for the resource from the provider.")
;;   (diff [resource] "Returns a vector of vectors of resources for [create update delete]")
;;   (cf [resource] "Returns the json represntation for cf")
;;   (tf [resource] "Returns the json representation for tf")
;;   (tf-id [resource] "eturns the ")
;;   (retrieve-raw-all [resource] "Returns all resources for the type.")
;;   (aws-id [resource] "ID assigned by AWS"))

(defprotocol VpnConnection
  (is-up? [resource] "Returns true if the VPN Connection is up")
  (is-static? [resource] "Returns true if the connection is static")
  (has-route? [resource route] "Returns true if it has the route"))

(defprotocol VirtualPrivateGateway
  (is-attached? [resource] "Returns true if the gateway is attached to a VPC."))

(defprotocol RouteTable
  (route-propogation? [route-table virtual-private-gateway] "Returns true if the route propogationfor the virtual provate gateway for the route table is turned on."))

(def commit-state (atom {}))
(def diff-state (atom {:to-create [] :inconsistent [] :to-delete []}))
(def problem-state (atom {:to-create [] :inconsistent [] :to-delete []}))
(def cf-state-generated (atom {}))
(def retrieved-state (atom {}))

(def resource-types [:customer-gateway])

(defn print-me [v]
  (println v)
  v)

(defn clear-all []
  (map #(reset! % {}) [commit-state diff-state retrieved-state])
  nil)

(defn unstage [& args]
  (reset! commit-state {}))

(defn clear-pull-state []
  (reset! retrieved-state {}))

(defn to-tags [tags-m]
  (let [if-keyword #(if (keyword? %) (name %) %) ]
    (mapv (fn [[k v]] {:key (if-keyword k) :value (if-keyword v)}) tags-m)))

(defn name-to-id [name] (kebab/->kebab-case-string name))

(defn id->name [id] (kebab/->Camel_Snake_Case_String id))

(defn almond-tags [{:keys [almonds-id stack-id tags] :or {tags {}}}]
  (merge {:almonds-id almonds-id
          :stack-id stack-id
          "Name" (id->name almonds-id)}
         tags))

(comment
  (to-tags {:hi "abc" "Name4" "qwe"})
  (almond-tags {:stack-id :central-stack :almonds-id :central-vpc :tags {"Name2" "hi"}})
  (id->name :g3))

(defn create-tags [resource-id tags]
  (aws-ec2/create-tags {:resources [resource-id] :tags tags}))

(defn add-tags [resource-id m]
  (->> (almond-tags m)
       (to-tags)
       (create-tags resource-id)))

(defn has-tag? [k v]
  (fn[{:keys [tags]}]
    (some (fn[{:keys [key value]}]
            (when (= key k)
              (= value v)))
          tags)))

(defn get-tag [k coll]
  (-> (filter (fn [{:keys [key]}]
                (= key k))
              coll)
      first
      :value
      keyword))

(get-tag "almonds-id" [{:key "almonds-id" :value "hello"}])

(defn add-almond-ids-from-tags [m]
  (let [almonds-id (get-tag "almonds-id" (:tags m))
        stack-id (get-tag "stack-id" (:tags m)) ]
    (merge m {:almonds-id almonds-id :stack-id stack-id})))

(add-almond-ids-from-tags {:tags [{:key "almonds-id" :value "hello"}
                                  {:key "stack-id" :value "dev-stack"}]})

(defn get-resource [almonds-id coll]
  (first (filter (has-tag? "almonds-id" almonds-id) coll)))

(defn get-stack-resources [stack-id resource-type]
  (->> {:almonds-type resource-type}
       (retrieve-raw-all)
       (filter (has-tag? "stack-id" (name stack-id)))))

(defn sanitize-resources [resource-type coll]
  (->> coll
       (map add-almond-ids-from-tags)
       (map (fn[m] (merge m {:almonds-type resource-type})))
       (map sanitize)))

(defn diff-resources [stageed-rs retrieved-rs]
  (let [d (->> (data/diff (into #{} stageed-rs)
                         (into #{} retrieved-rs))
              butlast
              (map (fn[coll] (map :almonds-id coll)))
              (map #(into #{} %))
              (zipmap [:to-create :to-delete]))]
    (merge d {:incosistent (last (data/diff (:to-delete d)
                                            (:to-create d)))})))

(comment (diff-resources (seq [{:almonds-id :g1 :a 1} {:almonds-id :g2 :a 2} {:almonds-id :g3 :a 2}])
                         (seq [{:almonds-id :g2 :a 2} {:almonds-id :g1 :a 2} {:almonds-id :g4 :a 2}])))

(defn diff-stack-resource [stack-id resource-type]
  (->> (stack-id @retrieved-state)
       (sanitize-resources resource-type)
       (diff-resources (->> @commit-state
                            stack-id
                            vals
                            (filter (fn [m] (= resource-type (:almonds-type m))))))))

(diff-stack-resource :murtaza-sandbox :customer-gateway)

(sanitize-resources :customer-gateway (:murtaza-sandbox @retrieved-state))

(defn retrieve-resource [m]
  (get-resource
   (:almonds-id m)
   (retrieve-raw-all m)))

(defn pull [stack-id]
  (swap! retrieved-state
         merge
         {stack-id (mapcat #(get-stack-resources stack-id %) resource-types)}))

(defn show-pull-state [stack-id]
  (stack-id @retrieved-state))

(defn has-value?
  "Expects a collection of maps. Returns true if any map in the collection has a matching key/value pair."
  [coll k v]
  (when
      (seq (filter (fn[m] (= (m k) v)) coll))
    true))

(defn exists? [resource]
  "Given a resource returns true if has been created with the provider. The resource should implement the retrieve-raw method of Resource and should have an :id."
  (when (retrieve-resource resource) true))

(defn validate-all [& fns]
  (fn [resource]
    (every? true? ((apply juxt fns) resource))))

(defn to-json [m]
  (generate-string m {:key-fn (comp name ->CamelCase)}))

(defn stage [stack-id coll]
  (->> coll
       (map (fn[m] (merge m {:stack-id stack-id})))
       (map (fn [resource]
              (when (validate resource)
                (swap! commit-state
                       #(update-in % [stack-id] merge {(:almonds-id resource) resource})))))))

(defn calculate-diff [stack-id]
  (apply merge-with
         concat
         (map #(diff-stack-resource stack-id %) resource-types)))

(defn populate-diff-state [stack-id diff-result]
  (let [diffed (map sanitize-resources)])(zipmap [:to-create :inconsistent :to-delete]
          (map (fn[op]
                 (reduce
                  (fn[coll almonds-id]
                    (-> (@commit-state stack-id)
                        almonds-id
                        (cons coll)))
                  []
                  (op diff-result)))
               [:to-create :inconsistent :to-delete])))

(comment (populate-diff-state :murtaza-sandbox
                              {:inconsistent #{}, :to-create #{:g2 :g3 :g1}, :to-delete #{}}))

(defn diff-stack [stack-id]
  (reset! diff-state
          (populate-diff-state stack-id
                               (calculate-diff stack-id))))

(comment  (diff-stack :murtaza-sandbox))

@diff-state

(def empty-diff? #(every? empty? (vals @diff-state)))

(defn create-resources []
  (doseq [r (:to-create @diff-state)]
    (create r)))

(defn delete-resources []
  (doseq [r (:to-delete @diff-state)]
    (delete r)))

(defn push [& {:keys [with-pull] :or {with-pull true}}]
  (when (empty-diff?) (throw+ {:operation :push :msg "No diffs to apply. Please diff-stack first."}))
  (create-resources)
  (delete-resources)
  (reset! diff-state {:to-create [] :inconsistent [] :to-delete []})
  (when with-pull (pull :a)))

(comment (stage :c [{:almonds-type :customer-gateway :almonds-id "b" :a 2}]))

;; (defn cf-all []
;;   (if (seq @commit-state)
;;     (do
;;       (reset! cf-state-generated
;;               (map cf (vals @commit-state))))
;;     (throw+ {:operation :cf-all :msg "Please commit resources first."})))



;; (defn apply-diff []
;;   (when (empty-diff?) (throw+ {:operation :diff-all :msg "No diffs to apply. Please diff-all first."}))
;;   (reset! problem-state {:to-create [] :inconsistent [] :to-delete []})
;;   (let [{:keys [to-create to-update to-delete]} @diff-state]
;;     (doseq [r to-create]
;;       (create r))
;;     (doseq [r to-update]
;;       (if (update? r)
;;         (update r)
;;         (swap! problem-state (fn[old-state] (update-in old-state :to-create conj)))))
;;     (doseq [r to-delete]
;;       (if (delete? r)
;;         (delete r)
;;         (swap! problem-state (fn[old-state] (update-in old-state :to-delete conj))))))
;;   (reset! diff-state nil))
