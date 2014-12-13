(ns almonds.utils
  (:require [clojure.set :refer [difference intersection]]
            [clojure.pprint :refer [pprint]]
            [cheshire.core :refer [generate-string]]
            [camel-snake-kebab.core :as kebab :refer [->CamelCase]]
            [amazonica.aws.ec2 :as aws-ec2]
            [almonds.contract :refer :all]
            [schema.core :as schema]
            [almonds.state :refer :all]))

(defn print-me [v]
  (println v)
  v)

(defn drop-val [v coll]
  (difference (into #{} coll) #{v}))

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

(defn validate-all [& fns]
  (fn [resource]
    (every? true? ((apply juxt fns) resource))))

(defn to-json [m]
  (generate-string m {:key-fn (comp name ->CamelCase)}))

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

(defn almonds->aws-tags [tags]
  (let [if-keyword (fn[v]
                     (if (keyword? v) (print-str v) v))]
    (mapv (fn [[k v]] {:key (if-keyword k) :value (if-keyword v)}) tags)))

(defn aws->almonds-tags [coll]
  (reduce (fn[m {:keys [key value]}]
            (merge m {(read-string key) (read-string value)}))
          {}
          coll))

(defn create-tags [resource-id tags]
  (aws-ec2/create-tags {:resources [resource-id] :tags tags}))

(defn ->almond-map [m]
  (let [{:keys [almonds-tags almonds-type]} (aws->almonds-tags (:tags m))]
    (-> (merge m {:almonds-tags almonds-tags :almonds-type almonds-type})
        (#(merge % {:almonds-aws-id ((aws-id-key %) %)})))))

(comment (->almond-map {:vpc-id 2 :tags [{:key ":almonds-tags" :value "[:a :b]"}
                                         {:key ":almonds-type" :value ":vpc"}]}))
(def resource-schema {:almonds-tags schema/Uuid :almonds-type (apply schema/enum @resource-types) schema/Keyword schema/Int})

(defn validate-schema [m]
  (schema/validate resource-schema m))

(comment (validate-schema {:almonds-tags (uuid) :almonds-type :customer-gateway :bgp-asn 655}))

(defn rule-type [b]
  (if (true? b) :egress :ingress))

(comment (coll-contains? 2 [3 2]))

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

(defn default-acl-entry? [{:keys [rule-number]}]
  (>= rule-number 32767))
