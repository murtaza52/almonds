(ns almonds.resource
  (:require [slingshot.slingshot :refer [throw+]]
            [clojure.pprint :refer [pprint]]
            [cheshire.core :refer [generate-string]]
            [camel-snake-kebab.core :as kebab :refer [->CamelCase]]
            [clojure.data.json :refer [write-str]]
            [amazonica.aws.ec2 :as aws-ec2]
            [clojure.set :refer [difference intersection]]))

(defmulti resource-factory :type)
(defmethod resource-factory :default [_]
  (throw+ {:operation "Trying to create a new resource using resource factory"
           :msg "Either :type was not given or is an incorrect value."} ))

(defprotocol Resource
  "Defines the various operations that can be performed on a resource"
  (create [resource] "Create the resource")
  (retrieve [resource] "Returns a record based on the data returned by the provider.")
  (sanitize [resource] "Returns the sanitized json")
  (delete [resource] "Delete the resource")
  (update [resource] "Updates the resource")
  (id [resource] "Uniquely identifies a resource")
  (delete? [resource] "Determines if is safe to delete the resource")
  (update? [resource] "Can the resource be updated")
  (validate [resource] "Validates the resource definition")
  (dependents [resource] "Returns a list of child resources")
  (retrieve-raw [resource] "Returns the raw data retrieved for the resource from the provider.")
  (diff [resource] "Returns a vector of vectors of resources for [create update delete]")
  (cf [resource] "Returns the json represntation for cf")
  (tf [resource] "Returns the json representation for tf")
  (tf-id [resource] "eturns the ")
  (retrieve-raw-all [resource] "Returns all resources for the type.")
  (aws-id [resource] "ID assigned by AWS"))

(defprotocol VpnConnection
  (is-up? [resource] "Returns true if the VPN Connection is up")
  (is-static? [resource] "Returns true if the connection is static")
  (has-route? [resource route] "Returns true if it has the route"))

(defprotocol VirtualPrivateGateway
  (is-attached? [resource] "Returns true if the gateway is attached to a VPC."))

(defprotocol RouteTable
  (route-propogation? [route-table virtual-private-gateway] "Returns true if the route propogationfor the virtual provate gateway for the route table is turned on."))

(def commit-state (atom {}))
(def diff-state (atom {:to-create [] :incosistent [] :to-delete []}))
(def problem-state (atom {:to-create [] :incosistent [] :to-delete []}))
(def cf-state-generated (atom {}))

(defn reset-state []
  (reset! commit-state {})
  (reset! diff-state {:to-create [] :incosistent [] :to-delete []}))

(defn to-tags [tags-m]
  (let [if-keyword #(if (keyword? %) (name %) %) ]
    (mapv (fn [[k v]] {:key (if-keyword k) :value (if-keyword v)}) tags-m)))

(defn name-to-id [name] (kebab/->kebab-case-string name))

(defn id->name [id] (kebab/->Camel_Snake_Case_String id))

(defn almond-tags [stack {:keys [id-tag tags] :or {tags {}}}]
  (merge {:id-tag id-tag
          :stack-tag stack
          "Name" (id->name id-tag)}
         tags))

(comment
  (to-tags {:hi "abc" "Name4" "qwe"})
  (almond-tags :central-stack {:id-tag :central-vpc :tags {"Name2" "hi"}}))

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
      :value))

(get-tag "id-tag" [{:key "id-tag" :value "hello"}])

(defn add-id-from-tag [m]
  (let [id-tag (get-tag "id-tag" (:tags m))]
    (merge m {:id-tag id-tag})))

(add-id-from-tag {:tags [{:key "id-tag" :value "hello"}]})

(defn get-resource [id-tag coll]
  (first (filter (has-tag? "id-tag" id-tag) coll)))

(defn get-stack-resources [stack-tag resource-type]
  (->> (resource-factory {:type resource-type})
       (retrieve-raw-all)
       (filter (has-tag? "stack-tag" (name stack-tag)))))

(defn sanitize-resources [resource-type coll]
  (->> coll
       (map add-id-from-tag)
       (map (fn[m] (merge m {:type resource-type})))
       (map resource-factory)
       (map sanitize)))

(defn diff-resources [commited-rs retrieved-rs]
  (let [r (->> retrieved-rs
               (map :id-tag)
               (into #{}))
        c (->> commited-rs
               (map :id-tag)
               (into #{}))]
    {:incosistent (intersection r c)
     :to-create (difference c r)
     :to-delete (difference r c)}))

(defn diff-stack-resource [stack-tag resource-type]
  (->> (get-stack-resources stack-tag resource-type)
       (sanitize-resources resource-type)
       (diff-resources (->> @commit-state
                            stack-tag
                            vals
                            (filter (fn [m] (= resource-type (:type m))))))))



;; get all resources for a stack and the type


(defn retrieve-resource [resource]
  (get-resource
   (:id-tag resource)
   (retrieve-raw-all resource)))

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

(def resource-types [:customer-gateway])

(defn commit [stack coll]
  (->> (map resource-factory coll)
       (map (fn [resource]
              (when (validate resource)
                (swap! commit-state
                       #(update-in % [stack] merge {(:id-tag resource) resource})))))))

(defn caculate-diff [stack-id]
  (apply merge-with
         concat
         (map #(diff-stack-resource stack-id %) resource-types)))

(let [stack-id :murtaza-sandbox op :to-create diff-result {:incosistent #{}, :to-create #{:g2 :g3 :g1}, :to-delete #{}}]
  (reduce (fn[id-tag]
            (-> (@commit-state stack-id)
                op
                id-tag))
          (op diff-result)))

(defn populate-commit-state [])

(defn diff-stack [stack-id]
  (if (seq @commit-state)
    (reset! diff-state
            (calculate-diff stack-id))
    (throw+ {:operation :diff-stack :msg "Please commit resources first."})))

(comment  (diff-stack :murtaza-sandbox))

(def empty-diff? #(every? empty? (vals @diff-state)))

(defn create-resources []
  (map create (:to-create @diff-state)))

(defn delete-resources []
  (map delete (:to-delete @diff-state)))

(defn apply-diff []
  (when (empty-diff?) (throw+ {:operation :apply-diff :msg "No diffs to apply. Please diff-stack first."}))
  (create-resources)
  (delete-resources)
  (reset! diff-state nil))

(comment (commit :c [{:type :customer-gateway :id-tag "b" :a 2}]))

(defn cf-all []
  (if (seq @commit-state)
    (do
      (reset! cf-state-generated
              (map cf (vals @commit-state))))
    (throw+ {:operation :cf-all :msg "Please commit resources first."})))



;; (defn apply-diff []
;;   (when (empty-diff?) (throw+ {:operation :diff-all :msg "No diffs to apply. Please diff-all first."}))
;;   (reset! problem-state {:to-create [] :incosistent [] :to-delete []})
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
