(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [environ.core :refer [env]]))

(almonds.core/set-aws-credentials (env :aws-access-key) (env :aws-secret) "https://ec2.amazonaws.com")

(require 'almonds.core :reload) ;; for compilation of macros

(def config {:log-ec2-calls true})

(almonds.core/set-config config)
