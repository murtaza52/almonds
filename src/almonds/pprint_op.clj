(ns almonds.pprint-op
  (:require [clojure.pprint :refer [pprint]]
            [almonds.utils :refer :all]))

(defn print-fn [& params]
  (newline)
  (println (str "===> " (name (first params)) " " (if (second params) (print-str (second params)) ""))))

(defn print-resource-group [[type resources]]
  (newline)
  (println (str ">> " (name type)))
  (doseq [r resources]
    (pprint r)))

(comment (print-resource-group [:instance [[:instance :a] [:instance :b]]]))

(defmulti pprint-op (fn[[f args] result] f))

(defmethod pprint-op :set-aws-credentials [_ _]
  (print-fn :set-aws-credentials)
  (println "Successfully connected to AWS."))

(defn get-almonds-tag [coll]
  (-> coll :almonds-tags vec))

(defmethod pprint-op :add [[f args] result]
  (print-fn f args)
  (when (seq result)
    (do (println "The following resources have been defined locally : ")
        (let [tags (map get-almonds-tag result)]
          (doseq [r (group-by-resource tags)] 
            (print-resource-group r))))))

(defn diff-print [[f args] {:keys [only-on-remote only-on-local]} print-str]
  (print-fn f args)
  (newline)

  (when (seq only-on-remote)
    (do (println (str "The following resources exist only on the cloud provider and have not been defined locally" print-str "Running sync-resources will delete them :"))
        (doseq [r (group-by-resource only-on-remote)] 
          (print-resource-group r))))
  
  (when (seq only-on-local)
    (do (println (str "The following resources have been defined locally and do not exists on the cloud provider" print-str "Running sync-resources will create them :"))
        (doseq [r (group-by-resource only-on-local)] 
          (print-resource-group r))))

  (when-not (or (seq only-on-remote) (seq only-on-local))
    (newline)
    (println "All resources defined locally also exist remotely and vice versa.")))

(defmethod pprint-op :diff-tags [cmd result]
  (let [v " (Only the almonds-tags of the resource is shown below). " ]
    (diff-print cmd result v)))

(defmethod pprint-op :diff [cmd result]
  (let [v ". " ]
    (diff-print cmd result v)))

(defmethod pprint-op :pull [[f args] result]
  (do
    (print-fn f args)
    (println "All resources have been retrieved from the remote provider.")))

(defmethod pprint-op :clear-all [[f args] result]
  (do
    (print-fn f args)
    (println "The local and remote state have been reset. Please pull and add to repopulate the state.")))

(defmethod pprint-op :default [[f args] result]
  (do
    (print-fn f args)
    (pprint result)))

(comment (pprint-op [:a 2]
                    [:t]))
