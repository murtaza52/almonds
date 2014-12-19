(ns almonds.core
  (:require [amazonica.core :as aws-core :refer [defcredential]]
            [almonds.api :as api]
            [almonds.state :as state]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; credentials ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn set-aws-credentials [aws-access-key aws-secret aws-url]
  (defcredential aws-access-key aws-secret aws-url))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; reset state ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(api/clear-all)

;;;;;;;;;;;;;;;; execute the handler calls ;;;;;;;;;;;;;;;
(require 'almonds.handler)

;;;;;;;;;;;;;;;; reset resources ;;;;;;;;;;;;;;;;;;;;;;
(state/reset-resource-types)
(require 'almonds.resources :reload)

(defn set-config [{:keys [log-ec2-calls]}]
  (reset! almonds.handler/log-ec2-calls log-ec2-calls))
