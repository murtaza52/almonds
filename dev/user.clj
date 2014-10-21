(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [almonds.resource :as r]
            [almonds.security-groups :as groups]
            [almonds.security-rules :as rules]))

;; (commit groups/gp3)

;; (diff-all)
(refresh)
