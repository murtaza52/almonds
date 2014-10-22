(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [almonds.resource :as r]
            [almonds.security-groups :as groups]
            [almonds.security-rules :as rules]))

(r/commit groups/gp3)
(commit pdp-in1)



(r/cf-all)

;; (commit groups/gp3)

;; (diff-all)
;; (refresh)
