(ns almonds.resource
  (:require [slingshot.slingshot :refer [throw+]]
            [clojure.pprint :refer [pprint]]
            [cheshire.core :refer [generate-string]]
            [camel-snake-kebab.core :refer [->CamelCase]]
            [clojure.data.json :refer [write-str]]))

(defprotocol Resource
  "Defines the various operations that can be performed on a resource"
  (create [resource] "Create the resource")
  (retrieve [resource] "Returns a record based on the data returned by the provider.")
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

(defn get-resource [id-tag coll]
  (first (filter
          (fn[{:keys [tags]}]
            (some (fn[{:keys [key value]}]
                    (when (= key "id-tag")
                      (= value id-tag)))
                  tags))
          coll)))

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
  "Given a resource returns true if has been created with the provider. The resource should implement the retrieve-raw method of Resource and should have an id-tag."
  (when (retrieve-resource resource) true))

(defn validate-all [& fns]
  (fn [resource]
    (every? true? ((apply juxt fns) resource))))

(defn to-json [m]
  (generate-string m {:key-fn (comp name ->CamelCase)}))

(defn commit [resource]
  (when (validate resource)
    (swap! commit-state merge {(id resource) resource})))

(defn diff-all []
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
