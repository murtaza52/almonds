(ns almonds.customer-gateway
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.resource :as r :refer [Resource retrieve-raw-all retrieve-raw exists? get-resource]]))

(def type-id :customer-gateway)

(defrecord CustomerGateway [id-tag bgp-asn ip-address tags]
  Resource
  (retrieve-raw-all [this]
    (:customer-gateways (aws-ec2/describe-customer-gateways)))
  (sanitize [this]
    (dissoc this :state :customer-gateway-id :type :tags))
  (create [this]
    (let [m (aws-ec2/create-customer-gateway {:type "ipsec.1" :bgp-asn bgp-asn :public-ip ip-address})]
      (r/create-tags (get-in m [:customer-gateway :customer-gateway-id]) tags)))
  (aws-id [this]
    (-> this get-resource))
  (validate [this] true)
  (cf [this]
    (r/to-json {id-tag {:type "AWS::EC2::CustomerGateway"
                      :properties {:bgp-asn bgp-asn
                                   :ip-address ip-address
                                   :tags [{:key "Name"
                                           :value id-tag}
                                          {:key "id-tag"
                                           :value id-tag}]}}})))

(defmethod r/resource-factory :customer-gateway [m]
  (map->CustomerGateway m))
