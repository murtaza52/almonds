(ns almonds.subnet
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.resource :as r :refer [Resource]]))

(defrecord Subnet [id-tag zone cidr vpc]
  Resource
  (retrieve-raw-all [this]
    (:subnets (aws-ec2/describe-subnets)))
  (tf [this]
    (r/to-json {"resource" {"aws_subnet" {id-tag
                                          {"vpc_id" (r/tf-id vpc)
                                           "cidr_block" cidr
                                           "availability_zone" zone}}}})))
