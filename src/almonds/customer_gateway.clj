(ns almonds.customer-gateway
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.resource :refer [Resource retrieve-raw-all retrieve-raw exists? get-resource]]))

(defrecord CustomerGateway [id-tag bgp-asn]
  Resource
  (retrieve-raw-all [this]
    (:customer-gateways (aws-ec2/describe-customer-gateways)))
  (cf [this]
    (to-json {id-tag {:type "AWS::EC2::CustomerGateway"
                      :properties {:bgp-asn bgp-asn
                                   :ip-address ip-address
                                   :tags [{:key "Name"
                                           :value id-tag}
                                          {:key }]}}})))
