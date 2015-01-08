(ns almonds.utils
  (:require [clojure.set :refer [difference intersection]]
            [clojure.pprint :refer [pprint]]
            [cheshire.core :refer [generate-string]]
            [camel-snake-kebab.core :as kebab]
            [amazonica.aws.ec2 :as aws-ec2]
            [almonds.contract :refer :all]
            [schema.core :as schema]
            [almonds.state :refer :all]
            [slingshot.slingshot :refer [try+]]
            [clojure.edn :as edn]))

(defn remove-nils-from-tags [coll]
  (into #{}
        (filterv (complement nil?) coll)))

(remove-nils-from-tags #{:a nil})

(defn print-me [v]
  (println v)
  v)

(defn drop-val [v coll]
  (difference (into #{} coll) #{v}))

(drop-val :a [:a :c])

(defn coll-contains? [v coll]
  (if ((into #{} coll) v)
    true
    false))

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

(defn contains-set? [set1 set2]
  (if (= (intersection set1 set2) set2)
    true false))

(contains-set? #{:a :b :c} #{})

(defn has-value?
  "Expects a collection of maps. Returns true if any map in the collection has a matching key/value pair."
  [coll k v]
  (when
      (seq (filter (fn[m] (= (m k) v)) coll))
    true))

(defn has-key-val? [k v m]
  (= v (k m)))

(has-key-val? :s 3 {:a 3})

(defn has-key? [k m]
  (when ((into #{} (keys m)) k) true))

(has-key? :b {:b 3})


(defn validate-all [& fns]
  (fn [resource]
    (every? true? ((apply juxt fns) resource))))

;; (defn to-json [m]
;;   (generate-string m {:key-fn (comp name ->CamelCase)}))

(defn name-to-id [name] (kebab/->kebab-case-string name))

(defn id->name [id] (kebab/->Camel_Snake_Case_String id))

(comment (id->name :a))

(defn tags->name [coll]
  (->> coll
       (map (fn[k] (if (keyword? k) k (print-str k))))
       (map kebab/->Camel_Snake_Case_String)
       (clojure.string/join "; ")))

(comment (tags->name [:a :b-c 2]))

(defn almonds-tags [{:keys [almonds-tags almonds-type tags] :or {tags {}}}]
  (merge {:almonds-tags (print-str almonds-tags)
          :almonds-type (print-str almonds-type)
          "Name" (tags->name almonds-tags)}
         tags))

(defn almonds->aws-tags [tags]
  (let [if-keyword (fn[v]
                     (if (keyword? v) (print-str v) v))]
    (mapv (fn [[k v]] {:key (if-keyword k) :value (if-keyword v)}) tags)))


(comment
  (almonds->aws-tags {:hi "abc" "Name4" "qwe"})
  (aws->almonds-tags [{:key ":name", :value "qwe"} {:key ":almonds-tags", :value "[:a :b]"}])
  (almonds-tags {:almonds-type :customer-gateway :almonds-tags [:a :b] :tags {"Name2" "hi"}})
  (id->name :g3))

(defn safe-reader [v]
  (try (edn/read-string v)
       (catch java.lang.RuntimeException e nil)))

(defn aws->almonds-tags [coll]
  (reduce (fn[m {:keys [key value]}]
            (merge m {(safe-reader key) (safe-reader value)}))
          {}
          coll))

(comment (safe-reader "arn:aws:cloudformation:us-east-1:790378854888:stack/CentralVpcTwVpn/1c131880-6993-11e4-bc94-50fa5262a89c")
         (safe-reader "{:a 2}"))

(defn create-tags [resource-id tags]
  (println (str "Creating aws-tags for " resource-id " with tags" (print-str tags)))
  (aws-ec2/create-tags {:resources [resource-id] :tags tags}))

(defn create-aws-tags [id m]
  (->> m
    almonds-tags
    almonds->aws-tags
    (create-tags id)))

(defn add-almonds-keys [m]
  (let [{:keys [almonds-tags almonds-type]} (aws->almonds-tags (:tags m))]
    (if (and almonds-tags almonds-type) 
      (merge m {:almonds-tags almonds-tags :almonds-type almonds-type})
      m)))

(comment (add-almonds-keys {:vpc-id 2 :tags [{:key ":almonds-tags" :value "[:a :b]"}
                                             {:key ":almonds-type" :value ":vpc"}]})
         (add-almonds-keys {:vpc-id 2}))

(defn add-almonds-aws-id [m]
  (try+
   (if-let [id (aws-id-key m)]
     (assoc m :almonds-aws-id ((aws-id-key m) m))
     m)
   (catch [:type :almonds.contract/bad-almonds-type] e
       m)))

(comment 
  (add-almonds-aws-id {:almonds-type :vpc, :almonds-tags [:a :b], :vpc-id 2})
  (add-almonds-aws-id {:a 2})
  (add-almonds-aws-id {:network-acl-id #{:web-tier :sandbox :network-acl :web-server},
                       :almonds-tags #{:network-acl-entry :web-tier :sandbox 32767 :egress :web-server},
                       :almonds-type :network-acl-entry,
                       :protocol :all,
                       :rule-number 32767,
                       :rule-action "deny",
                       :egress true,
                       :cidr-block "0.0.0.0/0"}))

;;(def resource-schema {:almonds-tags schema/Uuid :almonds-type (apply schema/enum @resource-types) schema/Keyword schema/Int})

;; (defn validate-schema [m]
;;   (schema/validate resource-schema m))

(comment (validate-schema {:almonds-tags (uuid) :almonds-type :customer-gateway :bgp-asn 655}))

(defn rule-type [b]
  (if (true? b) :egress :ingress))

(comment (coll-contains? 2 [3 2]))

(defn add-type [type tags]
  (into #{} (conj tags type)))

(add-type :a [:c :d])

(defn add-type-to-tags
  ([{:keys [almonds-type] :as m}]
     (add-type-to-tags :almonds-tags almonds-type m))
  ([tags-key type m]
     (update-in m
                [tags-key]
                #(add-type type %))))

(add-type-to-tags {:almonds-tags [:a] :almonds-type :abc})

(add-type-to-tags :vpc-id :vpc {:vpc-id [:a :b]})

(defn default-acl-entry? [{:keys [rule-number]}]
  (>= rule-number 32767))

(defn into-seq [coll]
  (->> coll
       (map #(if (seq %) (seq %) (rest '())))
       (map (fn[c] (map #(into [] %) c)))))

(defn is-dependent-on? [id m]
  (->> (vals m)
       (into #{})
       ((fn[s] (if (s id) true false)))))

(comment (is-dependent-on? "vpc-c44bd2a1" {:a "vpc-c44bd2a1"})
         (is-dependent-on? #{:a :b} {:s #{:a :b}}))
