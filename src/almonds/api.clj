(ns almonds.api
  (:require [almonds.state :refer :all]
            [almonds.contract :refer :all]
            [almonds.utils :refer :all]
            [slingshot.slingshot :refer [throw+ try+]]
            [clojure.data :as data]
            [clojure.set :refer [difference intersection]]
            [almonds.api-utils :refer :all]
            [amazonica.core :as aws-core :refer [defcredential]]
            [almonds.handler :as handler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; config ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-config [{:keys [log-ec2-calls verbose-mode] :or [false false]}]
  (reset! handler/log-ec2-calls log-ec2-calls)
  (reset! verbose-mode-state verbose-mode))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; credentials ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn is-connected? []
  (try+
   (amazonica.aws.ec2/describe-regions)
   (catch Object _
     (throw+ {:type :almonds/aws-connection-error :cause :almonds/aws-connection-error :msg "Unable to connect to AWS. Either check the credentials or the network connectivity. (describeRegions is used for checking the validity of the credentials. Your user should have sufficient permissions to perform this operation.)"}))))

(defn set-aws-credentials [aws-access-key aws-secret]
 (reset! aws-creds {:access-key aws-access-key :secret aws-secret}))

(defn set-aws-region [region]
  (defcredential (:access-key @aws-creds) (:secret @aws-creds) region)
  (is-connected?))

(comment (set-aws-credentials "a" "b")
         (set-aws-region "c")
         (amazonica.aws.ec2/describe-regions))

;;;;;;;;;;;;;;; clear states ;;;;;;;;;;;;;;;;;;;;;;;;

(defn clear-all []
  (reset! already-retrieved-remote? false)
  (doseq [state [local-state remote-state]]
    (reset! state {})))

(defn clear-remote-state []
  (reset! remote-state {}))

;;;;;;;;;;;;;;;;;;;;; retrieve resources ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn retrieve-resource [almonds-type]
  (->> {:almonds-type almonds-type}
       (retrieve-all)
       (map add-keys)))

(comment (retrieve-resource :network-acl))

(defn retrieve-dependents [resources]
  (->> resources
       (map dependents)
       (remove nil?)
       (remove empty?)
       flatten))

(comment (retrieve-dependents [{:description "Security_Group; 2; Classic",
                                :tags
                                [{:key ":almonds-tags", :value "#{:security-group 2 :classic}"}
                                 {:key "Name", :value "Security_Group; 2; Classic"}
                                 {:key ":almonds-type", :value ":security-group"}],
                                :ip-permissions [{:ip-protocol "tcp", :from-port 7015, :to-port 7015, :user-id-group-pairs [], :ip-ranges ["27.0.0.0/0"]}],
                                :group-id "sg-49619b24",
                                :almonds-tags #{:security-group 2 :classic},
                                :almonds-type :security-group,
                                :group-name "Security_Group; 2; Classic",
                                :ip-permissions-egress [],
                                :owner-id "790378854888",
                                :almonds-aws-id "sg-49619b24"}]))

(defn retrieve-resource-and-deps [almonds-type]
  (let [resources (retrieve-resource almonds-type)]
    (concat resources (retrieve-dependents resources))))

(comment (retrieve-resource-and-deps :security-group))

(defn pull-resource [almonds-type]
  (doall (map drop-from-remote-state (dependent-types {:almonds-type almonds-type})))
  (if (is-dependent? {:almonds-type almonds-type})
    (pull-resource (parent-type {:almonds-type almonds-type}))
    (do 
      (when verbose-mode? (println "Pulling almonds-type" almonds-type))
      (drop-from-remote-state almonds-type)
      (swap! remote-state #(concat %
                                   (retrieve-resource-and-deps almonds-type))))))

(comment (pull-resource :network-acl))

(defn sanitize-resources [resources]
  (->> resources
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
  (remove-empty (apply filter-resources @remote-state args)))

(defn get-remote [& args]
  (-> (apply get-remote-raw args)
      sanitize-resources
      remove-empty))

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
  (->> (data/diff (into #{} (->> (apply get-local-tags args)
                                 (map #(into #{} %))))
                  (into #{} (->> (apply get-remote-tags args)
                                 (map #(into #{} %)))))
       (into-seq)
       (zipmap [:only-in-local :only-on-remote])))

(defn diff [& args]
  (let [{:keys [only-on-remote only-in-local]} (apply diff-tags args)]
    {:only-in-local (mapcat #(apply get-local %) only-in-local) 
     :only-on-remote (mapcat #(apply get-remote %) only-on-remote)}))

(defn compare-resources [& args]
  {:in-local (apply get-local args)
   :on-remote (apply get-remote args)})

(defn compare-resources-raw [& args]
  (hash-map :in-local (apply get-local args)
            :on-remote (apply get-remote-raw args)))

;;;;;;;;;;;;;;; get ;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-only-remote [& args]
  (:only-on-remote (apply diff args)))

(defn get-only-local [& args]
  (:only-in-local (apply diff args)))

;;;;;;;;;;;; functions for manipulating state ;;;;;;;;;;;;;;;;;;;

(defn- add-resource
  [resource]
  (when (validate resource)
    (let [prepared-resource (-> resource
                                prepare-almonds-tags
                                pre-staging)
          deps (get-default-dependents prepared-resource)]
      (doall (map add-resource deps))
      (swap! local-state merge {(:almonds-tags prepared-resource)
                                prepared-resource})
      (concat [prepared-resource] deps))))

(defn- expel-resource [resource]
  (swap! local-state
         #(dissoc %
                  (:almonds-tags
                   (prepare-almonds-tags resource)))))

(defn add [v]
  (let [resources (read-resource v)]
    (if (map? resources)
      (add-resource resources)
      (flatten (doall (map add-resource resources))))))

(comment (add "/Users/murtaza/almonds_stack.clj"))

(defn expel [& args]
  (first (map expel-resource (apply get-local args))))

;;;;;;;;;;;;;;;;; ops ;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- delete-op! [coll]
  (doseq [r-type delete-sequence]
    (when-let [to-delete (seq (filter-resources coll r-type))]
      (doseq [v to-delete]
        (when verbose-mode? (println (str "Deleting " r-type " with :almonds-tags " (:almonds-tags v))))
        (delete v))
      (pull-resource r-type))))

(defn- create-op! [coll]
  (doseq [r-type create-sequence]
    (when-let [to-create (seq (filter-resources coll r-type))]
      (doseq [v to-create]
        (when verbose-mode? (do (println (str "Creating " r-type " with :almonds-tags " (:almonds-tags v)))
                                (println (print-str v))))
        (create v))
      (pull-resource r-type))))

(defn- recreate-op! 
  ([coll] (recreate-op! coll coll))
  ([delete-coll create-coll]
   (do (delete-op! delete-coll)
       (create-op! create-coll))))

;;;;;;;;;;; apply fns ;;;;;;;;;;;;;;;;;;;;;;

(defn sync-only-to-create [& args]
  (let [{:keys [only-in-local]} (apply diff args)]
    (create-op! only-in-local)))

(defn sync-only-to-delete [& args]
  (let [{:keys [only-on-remote]} (apply diff args)]
    (delete-op! only-on-remote)))

(defn sync-all [& args]
  (let [{:keys [only-in-local only-on-remote]} (apply diff args)]
    (delete-op! only-on-remote)
    (create-op! only-in-local)))

(defn recreate-resources [& args]
  (recreate-op! (apply get-remote args)
                (apply get-local args)))

(defn delete-resources [& args]
  (delete-op! (apply get-local args))
  (apply expel args))

(defn create-resources [& args]
  (create-op! (apply get-local args)))

(defn aws-id [almonds-tags]
  (let [resources (remove is-terminated? (apply get-remote-raw almonds-tags))]
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

(comment  (aws-id #{:security-group 2 :classic})
          (aws-id #{:instance 2 :dev-box}))


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
