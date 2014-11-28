(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [environ.core :refer [env]]
            [midje.repl :refer [autotest load-facts]]
            [amazonica.core :as aws-core :refer [defcredential]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; credentials ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defcredential (env :aws-access-key) (env :aws-secret) "https://ec2.amazonaws.com")

(autotest :pause)


;; (r/stage groups/gp3)
;; (stage pdp-in1)



;; (r/cf-all)

;; (stage groups/gp3)

;; (diff-all)
;; (refresh)
