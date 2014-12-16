(ns almonds.core
  (:require [amazonica.core :as aws-core :refer [defcredential]]
            [almonds.resources]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; credentials ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn set-aws-credentials [aws-access-key aws-secret aws-url]
  (defcredential aws-access-key aws-secret aws-url))
