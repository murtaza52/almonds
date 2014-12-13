(ns almonds.resource
  (:require [slingshot.slingshot :refer [throw+]]
            [clojure.pprint :refer [pprint]]
            [cheshire.core :refer [generate-string]]
            [camel-snake-kebab.core :as kebab :refer [->CamelCase]]
            [clojure.data.json :refer [write-str]]
            [amazonica.aws.ec2 :as aws-ec2]
            [amazonica.core :as amz]
            [clojure.set :refer [difference intersection]]
            [clojure.data :as data]
            [schema.core :as schema]))

(defmacro defmulti-with-default [name]
  `(do
     (defmulti ~name :almonds-type)
     (defmethod ~name :default [args#]
       (throw+ {:operation '~name
                :args (print-str args#)
                :msg "Either :type was not given or is an incorrect value."}))))

(defmulti-with-default create)
(defmulti-with-default sanitize)
(defmulti-with-default retrieve-all)
(defmulti-with-default validate)
(defmulti-with-default delete)
(defmulti-with-default aws-id-key)
(defmulti-with-default dependents)
(defmulti-with-default pre-staging)


(defprotocol VpnConnection
  (is-up? [resource] "Returns true if the VPN Connection is up")
  (is-static? [resource] "Returns true if the connection is static")
  (has-route? [resource route] "Returns true if it has the route"))

(defprotocol VirtualPrivateGateway
  (is-attached? [resource] "Returns true if the gateway is attached to a VPC."))

(defprotocol RouteTable
  (route-propogation? [route-table virtual-private-gateway] "Returns true if the route propogationfor the virtual provate gateway for the route table is turned on."))

(defn uuid [] (java.util.UUID/randomUUID))

(def index (atom {}))
(def pushed-state (atom {}))
(def remote-state (atom {}))
(def resource-types (atom []))

(def resource-schema {:almonds-tags schema/Uuid :almonds-type (apply schema/enum @resource-types) schema/Keyword schema/Int})

(defn validate-schema [m]
  (schema/validate resource-schema m))

(comment (validate-schema {:almonds-tags (uuid) :almonds-type :customer-gateway :bgp-asn 655}))

(defn print-me [v]
  (println v)
  v)

(defn clear-all []
  (doseq [a [index pushed-state remote-state]]
    (reset! a {})))

(defn drop-val [v coll]
  (clojure.set/difference (into #{} coll) #{v}))

(defn coll-contains? [v coll]
  (if ((into #{} coll) v)
    true
    false))

(defn rule-type [b]
  (if (true? b) :egress :ingress))

(comment (coll-contains? 2 [3 2]))

(defn clear-pull-state []
  (reset! remote-state {})
  (reset! pushed-state {}))

(defn almonds->aws-tags [tags]
  (let [if-keyword (fn[v]
                     (if (keyword? v) (print-str v) v))]
    (mapv (fn [[k v]] {:key (if-keyword k) :value (if-keyword v)}) tags)))

(defn aws->almonds-tags [coll]
  (reduce (fn[m {:keys [key value]}]
            (merge m {(read-string key) (read-string value)}))
          {}
          coll))

(defn name-to-id [name] (kebab/->kebab-case-string name))

(defn id->name [id] (kebab/->Camel_Snake_Case_String id))

(defn tags->name [coll]
  (->> coll
       (map (fn[k] (if (keyword? k) k (print-str k))))
       (map kebab/->Camel_Snake_Case_String)
       (clojure.string/join " : ")))

(tags->name [:a :b-c 2])

(defn almonds-tags [{:keys [almonds-tags almonds-type tags] :or {tags {}}}]
  (merge {:almonds-tags (print-str almonds-tags)
          :almonds-type (print-str almonds-type)
          "Name" (tags->name almonds-tags)}
         tags))

(comment
  (almonds->aws-tags {:hi "abc" "Name4" "qwe"})
  (aws->almonds-tags [{:key ":name", :value "qwe"} {:key ":almonds-tags", :value "[:a :b]"}])
  (almonds-tags {:almonds-type :customer-gateway :almonds-tags [:a :b] :tags {"Name2" "hi"}})
  (id->name :g3))

(defn create-tags [resource-id tags]
  (aws-ec2/create-tags {:resources [resource-id] :tags tags}))

(defn has-tag? [k v]
  (fn[{:keys [tags]}]
    (some (fn[{:keys [key value]}]
            (when (= key k)
              (= value v)))
          tags)))

(defn has-tag-key? [k]
  (fn[{:keys [tags]}]
    (some (fn[{:keys [key value]}]
            (when (= key k)
              true))
          tags)))

((has-tag-key? ":almonds-tags") {:a 2 :tags [{:key ":almonds-tags" :value "[:a :b]"}
                                             {:key ":almonds-type" :value "dev-stack"}]})

(defn get-tag [k coll]
  (-> (filter (fn [{:keys [key]}]
                (= key k))
              coll)
      first
      :value
      read-string))

(get-tag "almonds-tags" [{:key "almonds-tags" :value "hello"}])

(defn ->almond-map [m]
  (let [{:keys [almonds-tags almonds-type]} (aws->almonds-tags (:tags m))]
    (-> (merge m {:almonds-tags almonds-tags :almonds-type almonds-type})
        (#(merge % {:almonds-aws-id ((aws-id-key %) %)})))))

(comment (->almond-map {:vpc-id 2 :tags [{:key ":almonds-tags" :value "[:a :b]"}
                                         {:key ":almonds-type" :value ":vpc"}]}))

(defn pull-remote-state []
  (->> @resource-types
       (mapcat #(retrieve-all {:almonds-type %}))
       (reset! remote-state)))

(defn contains-set? [set1 set2]
  (if (= (intersection set1 set2) set2)
    true false))

(contains-set? #{:a :b :c} #{})

(defn filter-resources [coll & args]
  (filter (fn [{:keys [almonds-tags]}]
            (contains-set? (into #{} almonds-tags)
                           (into #{} args)))
          coll))

(comment (filter-resources @pushed-state))

(defn pull []
  (->> (pull-remote-state)
       (filter (has-tag-key? ":almonds-tags"))
       (filter (has-tag-key? ":almonds-type"))
       (map ->almond-map)
       ((fn [coll] (concat coll (mapcat dependents coll))))
       (reset! pushed-state)))

(defn pushed-resources-raw [& args]
  (when-not (seq @pushed-state) (pull))
  (apply filter-resources @pushed-state args))

(defn pushed-resources [& args]
  (->> (apply pushed-resources-raw args)
       (map sanitize)))

(defn pushed-resources-ids [& args]
  (->> (apply pushed-resources-raw args)
       (map :almonds-tags)))

(defn aws-id [almonds-tags]
  (let [resources (apply pushed-resources-raw almonds-tags)]
    (when-not (seq resources) (throw+ {:operation 'aws-id
                                       :args (print-str almonds-tags)
                                       :msg "Unable to find aws-id for the given almonds-tags."}))
    (when (< 1 (count resources)) (throw+ {:operation 'aws-id
                                           :args (print-str almonds-tags)
                                           :num-of-resources (count resources)
                                           :msg "Duplicate resources found for the given alomonds-tags. Please provide a unique tag."}))
    (-> resources first :almonds-aws-id)))

(defn aws-id->almonds-tags [aws-id]
  (let [tags (-> (filter (fn [{:keys [almonds-aws-id]}]
                           (= almonds-aws-id aws-id))
                         (pushed-resources-raw))
                 first
                 :almonds-tags)]
    (if tags tags (throw+ {:operation 'aws-id->almonds-tags
                           :args (print-str aws-id)
                           :msg "Unable to find almonds-tags for the given aws-id."}))))

(comment (aws-id->almonds-tags "vpc-f3eb7996"))

(defn staged-resources [& args]
  (when (seq @index)
    (apply filter-resources (vals @index) args)))

(defn staged-resources-ids [& args]
  (->> (apply staged-resources args)
       (map :almonds-tags)))

(comment (staged-resources))

(comment  (pushed-resources-raw 1)

          (sanitize {:almonds-type :customer-gateway,
                     :almonds-tags [:sandbox-stack :web-tier :sync-box 1],
                     :customer-gateway-id "cgw-b2e604db",
                     :state "available",
                     :type "ipsec.1",
                     :ip-address "125.12.14.111",
                     :bgp-asn "6500",
                     :tags
                     [{:value "Sandbox_Stack : Web_Tier : Sync_Box : 1", :key "Name"}
                      {:value "[:sandbox-stack :web-tier :sync-box 1]", :key ":almonds-tags"}
                      {:value ":customer-gateway", :key ":almonds-type"}]}))

(defn inconsistent-resources [coll]
  (remove (fn[tags] (= (apply pushed-resources tags) (apply staged-resources tags))) coll))

(defn diff-ids [& args]
  (->> (data/diff (into #{} (->> (apply staged-resources-ids args)
                                 (map #(into #{} %))))
                  (into #{} (->> (apply pushed-resources-ids args)
                                 (map #(into #{} %)))))
       (zipmap [:to-create :to-delete :inconsistent])
       (#(update-in % [:inconsistent] inconsistent-resources))))

(defn compare-resources [& args]
  (hash-map :staged (apply staged-resources args)
            :pushed (apply pushed-resources args)))

(defn compare-resources-raw [& args]
  (hash-map :staged (apply staged-resources args)
            :pushed (apply pushed-resources-raw args)))

(comment  (apply pushed-resources [2]))

(comment (compare-resources :s 2))

(comment (diff-ids :network-acl-entry 1))

(defn sanitize-resources []
  (->> @pushed-state
       (map ->almond-map)
       (map sanitize)))

(defn has-value?
  "Expects a collection of maps. Returns true if any map in the collection has a matching key/value pair."
  [coll k v]
  (when
      (seq (filter (fn[m] (= (m k) v)) coll))
    true))

(defn exists? [{:keys [almonds-tags]}]
  "Given a resource returns true if has been created with the provider. The resource should implement the retrieve-raw method of Resource and should have an :id."
  (when (seq (apply pushed-resources almonds-tags)) true))

(comment  (exists? {:almonds-tags [:sandbox-stack :web-tier :sync-box 1]}))

(defn validate-all [& fns]
  (fn [resource]
    (every? true? ((apply juxt fns) resource))))

(defn to-json [m]
  (generate-string m {:key-fn (comp name ->CamelCase)}))

(defn add-type-to-tags
  ([{:keys [almonds-type] :as m}]
     (add-type-to-tags :almonds-tags almonds-type m))
  ([tags-key type m]
     (update-in m
                [tags-key]
                (fn[tags]
                  (vec (if (coll-contains? type tags) tags (cons type tags)))))))

(add-type-to-tags {:almonds-tags [:a] :almonds-type :abc})

(add-type-to-tags :vpc-id :vpc {:vpc-id [:a :b]})

(defn stage-resource [resource]
  (when (validate resource)
    (-> resource
        (add-type-to-tags)
        (pre-staging)
        (#(hash-map (:almonds-tags %) %))
        (#(swap! index merge %)))))

(comment (stage-resource {:almonds-tags [:a :b] :almonds-type :customer-gateway}))

(defn stage [coll]
  (doall (map stage-resource coll))
  (staged-resources-ids))

(defn unstage [& args]
  (let [to-stage (->> (apply filter-resources (vals @index) args)
                      (into #{})
                      (difference (into #{} (vals @index))))]
    (reset! index {})
    (stage to-stage)))

(comment (diff-ids))

(defn diff [& args]
  (let [{:keys [inconsistent to-delete to-create]} (apply diff-ids args)]
    {:to-create (mapcat #(apply staged-resources %) to-create)
     :inconsistent (mapcat #(apply staged-resources %) inconsistent)
     :to-delete (mapcat #(apply pushed-resources %) to-delete)}))

(comment (pushed-resources-raw 1)
         (staged-resources))

(comment (diff))

(defn log-op [op resource-type ])

(defn delete-resources [coll]
  (doseq [r-type (reverse @resource-types)
          v (filter-resources coll r-type)]
    (println (str "Deleting " r-type " with :almonds-tags " (:almonds-tags v)))
    (delete v)))

(defn create-resources [coll]
  (doseq [r-type @resource-types
          v (filter-resources coll r-type)]
    (println (str "Creating " r-type " with :almonds-tags " (:almonds-tags v)))
    (create v)))

(defn push-without-pull [& args]
  (let [{:keys [to-create to-delete]} (apply diff args)]
    (delete-resources to-delete)
    (create-resources to-create)))

(defn push [& args]
  (apply push-without-pull args)
  (pull))

(comment (stage :c [{:almonds-type :customer-gateway :almonds-tags "b" :a 2}]))
