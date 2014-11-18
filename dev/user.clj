(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [environ.core :refer [env]]
            [midje.repl :refer [autotest load-facts]]
            [amazonica.core :as aws-core :refer [defcredential]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; credentials ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defcredential (env :aws-access-key) (env :aws-secret) "https://ec2.amazonaws.com")




;; (r/commit groups/gp3)
;; (commit pdp-in1)



;; (r/cf-all)

;; (commit groups/gp3)

;; (diff-all)
;; (refresh)
