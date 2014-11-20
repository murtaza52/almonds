(ns almonds.vpc
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [clojure.core.match :refer [match]]
            [dire.core :refer [with-handler!]]
            [slingshot.slingshot :refer [throw+]]
            [plumbing.core :refer [defnk]]
            [schema.core :as s]
            [clojure.set :refer [difference]]
            [almonds.resource :as r :refer [Resource id retrieve retrieve-raw create delete validate update dependents diff diff-all commit retrieve-raw-all exists? get-resource]]))

(defrecord VPC [id-tag]
  Resource
  (retrieve-raw-all [this]
    (:vpcs (aws-ec2/describe-vpcs)))
  (tf-id [this]
    (str "${aws_vpc." id-tag ".id}"))
  (validate [this] true)
  (diff [this]
    ))

(comment
  (def central (->VPC "central-vpc"))
  (def vpcs  (retrieve-raw-all central))
  (exists? central))
