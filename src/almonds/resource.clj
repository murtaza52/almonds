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
  (throw+ {:operation "Trying to create a new resourece using resourece factory"
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
(def diff-state (atom {:to-create [] :to-update [] :to-delete []}))
(def problem-state (atom {:to-create [] :to-update [] :to-delete []}))
(def cf-state-generated (atom {}))

(defn to-tags [m]
  (let [if-keyword #(if (keyword? %) (name %) %) ]
    (mapv (fn [[k v]] {:key (if-keyword k) :value (if-keyword v)}) m)))

(defn name-to-id [name] (kebab/->kebab-case-string name))

(defn id->name [id] (kebab/->Camel_Snake_Case_String id))

(defn add-tags [stack {:keys [id-tag tags] :or {tags {}}}]
  (to-tags (merge {:id-tag id-tag
                   :stack-tag stack
                   "Name" (id->name id-tag)}
                  tags)))

(comment
  (add-tags :central-stack {:id-tag :central-vpc :tags {"Name2" "hi"}}))

(defn create-tags [resource-id tags]
  (aws-ec2/create-tags {:resources [resource-id] :tags tags}))

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

(def rs [{:state "available",
          :type "ipsec.1",
          :customer-gateway-id "cgw-a58c6ecc",
          :tags
          [{:value "CentralVpcCustomerGatewayPrimary", :key "aws:cloudformation:logical-id"}
           {:value "CentralVpcTwVpn", :key "aws:cloudformation:stack-name"}
           {:value "Central VPC - Primary", :key "Name"}
           {:value
            "arn:aws:cloudformation:us-east-1:790378854888:stack/CentralVpcTwVpn/1c131880-6993-11e4-bc94-50fa5262a89c",
            :key "aws:cloudformation:stack-id"}
           {:value "central", :key "stack-tag"}
           {:value "CentralVpcCustomerGatewayPrimary", :key "id-tag"}],
          :bgp-asn "65000",
          :ip-address "182.72.16.113"}])

(sanitize-resources :customer-gateway rs)

(defn diff-resources [commited-rs retrieved-rs]
  (let [r (->> retrieved-rs
               (map :id-tag)
               (into #{}))
        c (->> commited-rs
               (map :id-tag)
               (into #{}))]
    {:to-update (intersection r c)
     :to-create (difference r c)
     :to-delete (difference r c)}))

(defn diff-stack-resource [stack-tag resource-type]
  (->> (get-stack-resources stack-tag resource-type)
       (sanitize-resources resource-type)
       (diff-resources (->> @commit-state
                            stack-tag
                            vals
                            (filter (fn [m] (= resource-type (:type m))))))))

(diff-stack-resource :central :customer-gateway)

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

(defn commit [stack coll]
  (->> (map resource-factory coll)
       (map (fn [resource]
              (when (validate resource)
                (swap! commit-state
                       #(update-in % [stack] merge {(:id-tag resource) resource})))))))

(defn rm
  "Deletes the AWS resources"
  [stack coll]
  true)

(comment (commit :c [{:type :customer-gateway :id-tag "b" :a 2}]))

(def resource-types [:customer-gateway])

(defn diff-stack [stack-id]
  (map #(diff-stack-resource stack-id %) resource-types))

(diff-stack :central)

(defn diff-all [stack]
  (if (seq @commit-state)
    (do
      (reset! diff-state
              (apply merge-with
                     concat
                     (map diff (vals @commit-state))))
      (pprint @diff-state))
    (throw+ {:operation :diff-all :msg "Please commit resources first."})))

(defn cf-all []
  (if (seq @commit-state)
    (do
      (reset! cf-state-generated
              (map cf (vals @commit-state))))
    (throw+ {:operation :cf-all :msg "Please commit resources first."})))

(def empty-diff? #(every? empty? (vals @diff-state)))

(defn apply-diff []
  (when (empty-diff?) (throw+ {:operation :diff-all :msg "No diffs to apply. Please diff-all first."}))
  (reset! problem-state {:to-create [] :to-update [] :to-delete []})
  (let [{:keys [to-create to-update to-delete]} @diff-state]
    (doseq [r to-create]
      (create r))
    (doseq [r to-update]
      (if (update? r)
        (update r)
        (swap! problem-state (fn[old-state] (update-in old-state :to-create conj)))))
    (doseq [r to-delete]
      (if (delete? r)
        (delete r)
        (swap! problem-state (fn[old-state] (update-in old-state :to-delete conj))))))
  (reset! diff-state nil))


(def retrieved-resources (atom {}))

;; retrieve all resources
;; group the resources by type - local and retrieved
;; another atom for diffs - {:to-create [] :to-delete [] :to-update []}
;; compare the resources based on identity, if found and not equal then to-update,
