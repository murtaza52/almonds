(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [environ.core :refer [env]]))

(require 'almonds.core :reload)

(defcredential (env :aws-access-key) (env :aws-secret) "https://ec2.amazonaws.com")
