(ns almonds.runner 
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [almonds.api-data :refer [execute-api]]
            [almonds.utils :as utils]
            [clojure.string :as str])
  (:gen-class))

(require 'almonds.core) ;;; for compilation

(def cli-options
  [["-v" "--version" "version of the library"]
   ["-c" "--config CONFIG" "config file"
    :parse-fn str/trim
    :validate [utils/file-exists? "Please provide a valid path to the file."]]
   ["-o" "--operations FILE" "path to the file declaring the operations to perform."
    :parse-fn str/trim
    :validate [utils/file-exists? "Please provide a valid path to the file."]]
   ["-h" "--help" "display all options"]])

(defn usage [options-summary]
  (->> ["almonds - a library for infrastructure automation."
        ""
        "Usage: almonds [options]"
        ""
        "Options:"
        ""
        "Calling almonds with a list of operations: almonds -c config.txt -o dev_stack.txt"
        ""
        options-summary
        ""
        "Please refer to the manual page for more information."]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    [options arguments errors summary]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (error-msg errors))
      (:version options) (exit 0 "almonds version 0.3.2")
      (:operations options) (if (:config options)
                              (exit 0 (execute-api (:operations options) (:config options)))
                              (exit 1 "Please specify the config file.")))))

(comment (-main "-o /Users/murtaza/almonds_commands.clj")
         (-main "-v"))
