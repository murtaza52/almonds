(ns almonds.core
  (:require [environ.core :refer [env]]
            [amazonica.core :as aws-core :refer [defcredential]]
            [almonds.resources]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; credentials ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defcredential (env :aws-access-key) (env :aws-secret) "https://ec2.amazonaws.com")
