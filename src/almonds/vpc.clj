(ns almonds.vpc
  (:require [amazonica.core :as aws-core :refer [defcredential]]
            [amazonica.aws.ec2 :as aws-ec2]
            [clojure.core.match :refer [match]]
            [dire.core :refer [with-handler!]]
            [slingshot.slingshot :refer [throw+]]
            [plumbing.core :refer [defnk]]
            [schema.core :as s]
            [almonds.security-rules :as rules]
            [clojure.set :refer [difference]]
            [almonds.resource :refer [Resource id retrieve retrieve-raw create delete validate update dependents diff diff-all commit]]))

;;(aws-ec2/)

;; (defrecord VPC []
;;   Resource
;;   )

;; (amazonica.aws.ec2/)
