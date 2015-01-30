(ns user
  (:require [environ.core :refer [env]]
            [almonds.api :as api]))

(require 'almonds.core :reload) ;; for compilation of macros

(def config {:log-ec2-calls false
             :verbose-mode true})

(api/set-aws-credentials (-> env :aws :access-key) (-> env :aws :secret))
(api/set-aws-region (-> env :aws :region))



(api/set-config config)
