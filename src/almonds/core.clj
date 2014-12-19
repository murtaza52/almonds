(ns almonds.core
  (:require [amazonica.core :as aws-core :refer [defcredential]]
            [almonds.api :as api]
            [almonds.resources]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; credentials ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn set-aws-credentials [aws-access-key aws-secret aws-url]
  (defcredential aws-access-key aws-secret aws-url))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; reset state ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(api/clear-all)

