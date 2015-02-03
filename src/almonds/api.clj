(ns almonds.api
  (:require [almonds.state :refer :all]
            [almonds.contract :refer :all]
            [almonds.utils :refer :all]
            [slingshot.slingshot :refer [throw+ try+]]
            [clojure.data :as data]
            [clojure.set :refer [difference intersection] :as set]
            [almonds.api-utils :refer :all]
            [amazonica.core :as aws-core :refer [defcredential]]
            [almonds.handler :as handler]
            [circuit-breaker.core :as cb]
            [amazonica.aws.ec2 :as aws-ec2]))

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

(defn set-aws-bucket [v]
  (reset! aws-bucket-name v))

;;;;;;;;;;;;;;; clear states ;;;;;;;;;;;;;;;;;;;;;;;;

(defn clear-all []
  (reset! already-retrieved-remote? false)
  (doseq [state [local-state remote-state]]
    (reset! state {})))

(defn clear-remote-state []
  (reset! remote-state {}))

(defn set-stack [v] (reset! stack v))

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
                                [{:value "#{:security-group 2 :classic}", :key ":almonds-tags"}
                                 {:value ":default", :key ":almonds-stack"}
                                 {:value "Security_Group; 2; Classic", :key "Name"}
                                 {:value ":security-group", :key ":almonds-type"}],
                                :ip-permissions [{:ip-protocol "tcp", :from-port 7015, :to-port 7015, :user-id-group-pairs [], :ip-ranges ["27.0.0.0/0"]}],
                                :group-id "sg-c104c6ac",
                                :almonds-tags #{:security-group 2 :classic},
                                :almonds-type :security-group,
                                :almonds-stack :default,
                                :group-name "Security_Group; 2; Classic",
                                :ip-permissions-egress [],
                                :owner-id "790378854888",
                                :almonds-aws-id "sg-c104c6ac"}]))

(defn retrieve-resource-and-deps [almonds-type]
  (let [resources (retrieve-resource almonds-type)]
    (concat resources (retrieve-dependents resources))))

(comment (retrieve-resource :security-group))
(comment (retrieve-resource-and-deps :security-group))

(defn sanitize-resources [resources]
  (->> resources
       (filter #(:almonds-type %))
       (map sanitize)))

(defn filter-by-stack [coll]
  (filter #(= (get-stack) (:almonds-stack %)) coll))

(comment (filter-by-stack [{:almonds-stack :dev} {:almonds-stack :default}]))

(defn pull
  ([] (do (set-already-retrieved-remote)
          (flatten (doall (map pull pull-sequence)))))
  ([almonds-type] (do (doall (map drop-from-remote-state (dependent-types {:almonds-type almonds-type})))
                      (if (is-dependent? {:almonds-type almonds-type})
                        (pull (parent-type {:almonds-type almonds-type}))
                        (do
                          (when verbose-mode? (println "Pulling almonds-type" almonds-type))
                          (drop-from-remote-state almonds-type)
                          (let [resources (filter-by-stack (retrieve-resource-and-deps almonds-type))]
                            (swap! remote-state #(concat % resources))
                            resources))))))

(comment (pull))
(comment (pull :network-acl))

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
  (if (diff-maps
       (first (apply get-remote almonds-tags))
       (first (apply get-local almonds-tags)))
    false
    true))

(defn diff-tags [& args]
  (->> (data/diff (into #{} (->> (apply get-local-tags args)
                                 (map #(into #{} %))))
                  (into #{} (->> (apply get-remote-tags args)
                                 (map #(into #{} %)))))
       (into-seq)
       (zipmap [:only-in-local :only-on-remote :inconsistent])
       (#(update-in % [:inconsistent] (fn[v] (remove is-consistent? v))))))

(comment (diff-tags))

(defn diff [& args]
  (let [{:keys [only-on-remote only-in-local inconsistent]} (apply diff-tags args)]
    {:only-in-local (mapcat #(apply get-local %) only-in-local) 
     :only-on-remote (mapcat #(apply get-remote %) only-on-remote)
     :inconsistent (mapcat #(apply get-local %) inconsistent)}))

(comment (diff))

(defn diff-resource [tags]
  (zipmap [:in-local :on-remote] (diff-maps (first (apply get-local tags)) (first (apply get-remote tags)))))

(comment (diff-resource [:instance])
         (diff-resource [:web-tier :sandbox :web-server :subnet]))

(defn compare-resources [& args]
  (hash-map :in-local (apply get-local args)
            :on-remote (apply get-remote-raw args)))

;;;;;;;;;;;;;;; get ;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-inconsistent [& args]
  (:inconsistent (apply diff args)))

(defn get-only-on-remote [& args]
  (:only-on-remote (apply diff args)))

(defn get-only-on-local [& args]
  (:only-in-local (apply diff args)))

;;;;;;;;;;;; functions for manipulating state ;;;;;;;;;;;;;;;;;;;

(defn- add-resource
  [resource]
  (when (validate resource)
    (let [prepared-resource (-> resource
                                prepare-almonds-tags
                                add-stack-key
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
  (first (doall (map expel-resource (apply get-local args)))))

;;;;;;;;;;;;;;;;; ops ;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment  (cb/defncircuitbreaker :crud {:timeout 10 :threshold 2})

          (cb/wrap-with-circuit-breaker :crud
                                        (fn[] 
                                          (println "helo work")
                                          (throw+ {:msg "error"}))))

(defn- delete-op! [coll]
  (doseq [r-type delete-sequence]
    (when-let [to-delete (seq (filter-resources coll r-type))]
      (doseq [v to-delete]
        (when verbose-mode? (do
                              (newline)
                              (println (str "Deleting " r-type " with :almonds-tags " (:almonds-tags v)))))
        (delete v))
      (pull r-type))))

(defn- create-op! [coll]
  (doseq [r-type create-sequence]
    (when-let [to-create (seq (filter-resources coll r-type))]
      (doseq [v to-create]
        (when verbose-mode? (do
                              (newline)
                              (println (str "Creating " r-type " with :almonds-tags " (:almonds-tags v)))
                              (println (print-str v))))
        (create v))
      (pull r-type))))

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

(defn sync-only-inconsistent [& args]
  (let [{:keys [inconsistent]} (apply diff args)]
    (recreate-op! inconsistent)))

(defn sync-all [& args]
  (let [{:keys [only-in-local only-on-remote inconsistent]} (apply diff args)]
    (delete-op! only-on-remote)
    (recreate-op! inconsistent)
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

(comment  (aws-id #{:security-group 2 :classic})
          (aws-id #{:instance 2 :dev-box}))


(defn find-deps-aws-id [id]
  (filter #(is-dependent-on? id %) (get-remote-raw)))

(defn direct-deps [m]
  (when-let [almonds-tags (:almonds-tags (get-local-resource m))]
    (filter #(is-dependent-on? almonds-tags %) (get-local))))

(defn find-all-deps 
  ([m]
   (find-all-deps m []))
  ([m all-deps] 
   (if-not (seq (direct-deps m))
     all-deps
     (reduce (fn [result this-dep] 
               (if-not (contains? result this-dep)
                 (find-all-deps this-dep (conj result this-dep))
                 result))
             (direct-deps m)
             #{}))))

(find-all-deps {:almonds-type :vpc
                :almonds-tags [:sandbox :web-tier]
                :cidr-block "10.3.0.0/16"
                :instance-tenancy "default"})

(direct-deps {:almonds-type :vpc
              :almonds-tags [:sandbox :web-tier]
              :cidr-block "10.3.0.0/16"
              :instance-tenancy "default"})

(defn delete-deps-aws-id [id]
  (delete-op! (find-deps-aws-id id)))

;; http://stackoverflow.com/questions/1879885/clojure-how-to-to-recur-upon-exception
(defn try-times*
  "Executes thunk. If an exception is thrown, will retry. At most n retries
  are done. If still some exception is thrown it is bubbled upwards in
  the call chain."
  [n sleep-time thunk]
  (loop [n n]
    (if-let [result (try
                      [(thunk)]
                      (catch Exception e
                        (when (zero? n)
                          (throw e))))]
      (result 0)
      (do
        (Thread/sleep sleep-time)
        (recur (dec n))))))

(defmacro try-times
  "Executes body. If an exception is thrown, will retry. At most n retries
  are done. If still some exception is thrown it is bubbled upwards in
  the call chain."
  [n sleep-time & body]
  `(try-times* ~n ~sleep-time (fn [] ~@body)))

(comment (try-times 5 500 (do 
                            (println "throwing an error")
                            (throw+ {:msg "boy this is bad"}))))

(defn wait-on-state
  ([f-check f-execute f-refresh f-error f-success] 
   (wait-on-state f-check f-execute f-refresh f-error f-success 5 1000))
  ([f-check f-execute f-refresh f-error f-success tries sleep-time]
   (loop [n tries]
     (if (< n 0)
       (f-error)
       (if (f-check)
         (do (f-execute)
             (f-success))
         (do (Thread/sleep sleep-time)
             (f-refresh)
             (recur (- n 1))))))))

(comment (wait-on-state is-running? #(println "Hello World") [:dev-box :instance] (fn[](throw+ {:msg "Unable to attach eip to the instance"})) 10 1000))

(defn create-tags [resource-id tags]
  (try-times 5
             2000 
             (when verbose-mode? (println (str "Creating aws-tags for " resource-id ".")))
             (aws-ec2/create-tags {:resources [resource-id] :tags tags})))

(defn create-aws-tags [id m]
  (->> m
       almonds-tags
       almonds->aws-tags
       (create-tags id)))
