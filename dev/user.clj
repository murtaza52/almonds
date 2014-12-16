(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [almonds.core]
            [environ.core :refer [env]]))

(defcredential (env :aws-access-key) (env :aws-secret) "https://ec2.amazonaws.com")

;;[midje.repl :refer [autotest load-facts]]
;;(autotest :pause)
