(ns almonds.api-data
  (:require [almonds.api :as api :refer :all]
            [almonds.utils :refer :all]
            [almonds.pprint-op :refer [pprint-op]]
            [slingshot.slingshot :refer [try+ throw+]]
            [almonds.state :as state]))

(defn print-results [cmds results]
  (newline)
  (println "#######################################################################")
  (newline)
  (println "Results of running almonds operations:")
  (doall (map pprint-op
              cmds
              results))
  (newline)
  (println "#######################################################################"))

(comment (print-results [[:a 2] [:b 3]]
                        [[:t] :a]))

(defn set-cli-config [config]
  (newline)
  (println "Setting options provided from the config file.")
  (when-let [aws (:aws config)]
    (newline)
    (println "Setting AWS credentials.")
    (set-aws-credentials (:access-key aws) (:secret aws))
    (when-let [region (:region aws)]
      (println (str "Setting AWS region " region ". Testing the connection."))
      (set-aws-region region)))

  (when-let [stack (:stack config)]
    (newline)
    (println "Setting the :stack as - " stack)
    (reset! state/stack stack))
  
  (when-let [files (:resource-files config)]
    (newline)
    (println "Adding resources from the following files : ")
    (doseq [f files]
      (pprint f)
      (api/add f))))

(defn execute-api [cmds config]
  (set-cli-config config)
  (let [coll (read-resource cmds)] 
    (->> (for [[fn & args] coll]
           (apply (eval (symbol "almonds.api" (name fn)))
                  args))
         (print-results coll))))

(comment (def input [[:get-remote :instance]
                     [:get-local]
                     [:diff-tags]])
         (def input2 [[:set-aws-credentials "a" "b" "c"] [:error] [:hi]])
         (def file-input "/Users/murtaza/almonds_commands.clj")
         (execute-api file-input))
