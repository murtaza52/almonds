(ns almonds.api
  (:require [almonds.state :refer :all]
            [almonds.contract :refer :all]
            [almonds.utils :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [clojure.data :as data]
            [clojure.set :refer [difference intersection]]
            [almonds.api-utils :refer :all]))

(defn drop-from-remote-state [almonds-type]
  (swap! remote-state
         (fn[coll]
           (remove
            (fn[resource]
              (= almonds-type (:almonds-type resource)))
            coll))))

(comment (drop-from-remote-state :vpc))

(defn add-to-remote-state [resources]
  (let [coll (->> resources
                  (map add-almonds-keys)
                  (map add-almonds-aws-id)
                  ((fn [coll] (concat coll (doall (mapcat dependents coll))))))]
    (swap! remote-state #(concat % coll))
    coll))

(defn pull-resource [almonds-type]
  (doall (map drop-from-remote-state (dependent-types {:almonds-type almonds-type})))
  (if (is-dependent? {:almonds-type almonds-type})
    (pull-resource (parent-type {:almonds-type almonds-type}))
    (do 
      (println "Pulling almonds-type" almonds-type)
      (drop-from-remote-state almonds-type)
      (add-to-remote-state (retrieve-all {:almonds-type almonds-type})))))

(comment (pull-resource :network-acl))

(defn sanitize-resources [resources]
  (->> resources
       (filter #(:almonds-tags %))
       (filter #(:almonds-type %))
       (map sanitize)))

(defn pull []
  (set-already-retrieved-remote)
  (doall (map pull-resource pull-sequence))
  @remote-state)

(comment (pull))

;;;;;;;;;;;;;; get-functions for filtering remote and local resources ;;;;;;;;;;;;;;;;;;;;;;;

(defn get-remote-raw [& args]
  (when (take-pull?) (pull))
  (apply filter-resources @remote-state args))

(defn get-remote [& args]
  (sanitize-resources (apply get-remote-raw args)))

(defn get-remote-tags [& args]
  (->> (apply get-remote args)
       (map :almonds-tags)))

(defn get-local [& args]
  (apply filter-resources (vals @local-state) args))

(defn get-local-tags [& args]
  (->> (apply get-local args)
       (map :almonds-tags)))

(defn get-local-resource [resource]
  (->> (add-type-to-tags resource)
       :almonds-tags
       (apply get-local)
       first))

;;;;;;;;;;;;;;;;; compare ;;;;;;;;;;;;;;;;;;;;;;

(defn is-consistent? [almonds-tags]
  (= (apply get-remote almonds-tags) (apply get-local almonds-tags)))

(defn diff-tags [& args]
  (letfn [(inconsistent-resources [coll]
            (remove is-consistent? coll))]
    (->> (data/diff (into #{} (->> (apply get-local-tags args)
                                   (map #(into #{} %))))
                    (into #{} (->> (apply get-remote-tags args)
                                   (map #(into #{} %)))))
         (into-seq)
         (zipmap [:only-in-local :only-on-remote :inconsistent])
         (#(update-in % [:inconsistent] inconsistent-resources)))))

(defn diff [& args]
  (let [{:keys [inconsistent only-on-remote only-in-local]} (apply diff-tags args)]
    {:only-in-local (mapcat #(apply get-local %) only-in-local)
     :inconsistent (mapcat #(apply get-local %) inconsistent)
     :only-on-remote (mapcat #(apply get-remote %) only-on-remote)}))

(defn compare-resources [& args]
  {:in-local (apply get-local args)
   :on-remote (apply get-remote args)})

(defn compare-resources-raw [& args]
  (hash-map :in-local (apply get-local args)
            :on-remote (apply get-remote-raw args)))

;;;;;;;;;;;;;;; get ;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-inconsistent [& args]
  (:inconsistent (apply diff args)))

(defn get-only-remote [& args]
  (:only-on-remote (apply diff args)))

(defn get-only-local [& args]
  (:only-in-local (apply diff args)))

;;;;;;;;;;;; functions for manipulating state ;;;;;;;;;;;;;;;;;;;

(defn add-resource
  [resource]
  (when (validate resource)
    (let [prepared-resource (-> resource
                                prepare-almonds-tags
                                pre-staging)]
      (doall (map add-resource (get-default-dependents prepared-resource)))
      (swap! local-state merge {(:almonds-tags prepared-resource)
                                prepared-resource}))))

(-> {:almonds-type :security-rule, :group-id [:sandbox :web-tier :app-box], :egress true, :cidr-ip "0.0.0.0/0", :ip-protocol "-1"}
    prepare-almonds-tags
    pre-staging)

(defn add [resources]
  (if (map? resources)
    (add-resource resources)
    (last (map add-resource resources))))

(defn expel-resource [resource]
  (swap! local-state
         #(dissoc %
                  (:almonds-tags
                   (prepare-almonds-tags resource)))))

(defn expel [& args]
  (first (map expel-resource (apply get-local args))))

;;;;;;;;;;;;;;;;; ops ;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- delete-op! [coll]
  (doseq [r-type delete-sequence]
    (when-let [to-delete (seq (filter-resources coll r-type))]
      (doseq [v to-delete]
        (println (str "Deleting " r-type " with :almonds-tags " (:almonds-tags v)))
        (delete v))
      (pull-resource r-type))))

(defn- create-op! [coll]
  (doseq [r-type create-sequence]
    (when-let [to-create (seq (filter-resources coll r-type))]
      (doseq [v to-create]
        (println (str "Creating " r-type " with :almonds-tags " (:almonds-tags v)))
        (println (print-str v))
        (create v))
      (pull-resource r-type))))

(defn- recreate-op! 
  ([coll] (recreate-op! coll coll))
  ([delete-coll create-coll]
   (do (delete-op! delete-coll)
       (create-op! create-coll))))

;;;;;;;;;;; apply fns ;;;;;;;;;;;;;;;;;;;;;;

(defn sync-only-create [& args]
  (let [{:keys [only-in-local]} (apply diff args)]
    (create-op! only-in-local)))

(defn sync-only-delete [& args]
  (let [{:keys [only-on-remote]} (apply diff args)]
    (delete-op! only-on-remote)))

(defn sync-only-inconsistent [& args]
  (let [{:keys [inconsistent]} (apply diff args)]
    (recreate-op! inconsistent)))

(defn sync-resources [& args]
  (let [{:keys [only-in-local only-on-remote inconsistent]} (apply diff args)]
    (delete-op! only-on-remote)
    (create-op! only-in-local)
    (recreate-op! inconsistent)))

(defn recreate [& args]
  (recreate-op! (apply get-remote args)
                (apply get-local args)))

(defn delete-resources [& args]
  (delete-op! (apply get-local args))
  (apply expel args))

(defn aws-id [almonds-tags]
  (let [resources (apply get-remote-raw almonds-tags)]
    (when-not (seq resources) (throw+ {:operation 'aws-id
                                       :args (print-str almonds-tags)
                                       :msg "Unable to find aws-id for the given almonds-tags."}))
    (when (< 1 (count resources)) (throw+ {:operation 'aws-id
                                           :args (print-str almonds-tags)
                                           :num-of-resources (count resources)
                                           :resources (print-str resources)
                                           :msg "Duplicate resources found for the given alomonds-tags. Please provide a unique tag."}))
    (-> resources first :almonds-aws-id)))

(defn aws-id->almonds-tags [aws-id]
  (let [tags (-> (filter (fn [{:keys [almonds-aws-id]}]
                           (= almonds-aws-id aws-id))
                         (get-remote-raw))
                 first
                 :almonds-tags)]
    (if tags tags (throw+ {:operation 'aws-id->almonds-tags
                           :args (print-str aws-id)
                           :msg "Unable to find almonds-tags for the given aws-id."}))))



(defn find-deps-aws-id [id]
  (filter #(is-dependent-on? id %) (get-remote-raw)))

(defn find-deps [m]
  (when-let [almonds-tags (:almonds-tags (get-local-resource m))]
    (filter #(is-dependent-on? almonds-tags %) (get-local))))

;; (defn find-all-deps 
;;   ([m]
;;    (find-all-deps m []))
;;   ([m all-deps] 
;;     (let [direct-deps (find-deps m)]
;;       (if-not (seq direct-deps)
;;         all-deps
;;         (map #(recur % (concat all-deps %)) direct-deps)))))

(defn delete-deps-aws-id [id]
  (delete-op! (find-deps-aws-id id)))

