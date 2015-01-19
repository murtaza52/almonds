(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [environ.core :refer [env]]
            [clojure.tools.trace :refer :all]))

(almonds.core/set-aws-credentials (env :aws-access-key) (env :aws-secret) "https://ec2.amazonaws.com")

(require 'almonds.core :reload) ;; for compilation of macros

(def config {:log-ec2-calls false})

(almonds.core/set-config config)

(def all-ns '[almonds.resource almonds.state almonds.api almonds.contract almonds.resources almonds.resources.security-rule almonds.resources.security-group])

;;(trace-ns almonds.api)

;; (for [v all-ns]
;;   (trace-ns v))

;;(trace-ns)
