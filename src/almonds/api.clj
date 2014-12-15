(ns almonds.api
  (:require [almonds.state :refer :all]
            [almonds.contract :refer :all]
            [almonds.utils :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [clojure.data :as data]
            [clojure.set :refer [difference intersection]]))

(defn clear-all []
  (doseq [a [index pushed-state remote-state]]
    (reset! a {})))

(defn clear-pull-state []
  (reset! remote-state {})
  (reset! pushed-state {}))

(defn pull-remote-state []
  (->> @resource-types
       (mapcat #(retrieve-all {:almonds-type %}))
       (reset! remote-state)))

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

(defn pushed-resources-tags [& args]
  (->> (apply pushed-resources-raw args)
       (map :almonds-tags)))

(defn staged-resources [& args]
  (when (seq @index)
    (apply filter-resources (vals @index) args)))

(defn staged-resources-tags [& args]
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


(defn inconsistent-resources [coll]
  (remove (fn[tags] (= (apply pushed-resources tags) (apply staged-resources tags))) coll))

(defn diff-tags [& args]
  (->> (data/diff (into #{} (->> (apply staged-resources-tags args)
                                 (map #(into #{} %))))
                  (into #{} (->> (apply pushed-resources-tags args)
                                 (map #(into #{} %)))))
       (into-seq)
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

(comment (diff-tags :network-acl-entry 1))

(defn sanitize-resources []
  (->> @pushed-state
       (map ->almond-map)
       (map sanitize)))

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
  (staged-resources-tags))

(defn unstage [& args]
  (let [to-stage (->> (apply filter-resources (vals @index) args)
                      (into #{})
                      (difference (into #{} (vals @index))))]
    (reset! index {})
    (stage to-stage)))

(comment (diff-tags))

(defn diff [& args]
  (let [{:keys [inconsistent to-delete to-create]} (apply diff-tags args)]
    {:to-create (mapcat #(apply staged-resources %) to-create)
     :inconsistent (mapcat #(apply staged-resources %) inconsistent)
     :to-delete (mapcat #(apply pushed-resources %) to-delete)}))

(comment (pushed-resources-raw 1)
         (staged-resources))

(comment (diff))

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
