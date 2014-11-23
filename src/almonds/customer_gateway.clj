(ns almonds.customer-gateway
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.resource :as r :refer [Resource retrieve-raw-all retrieve-raw exists? get-resource]]))

(def type-id :customer-gateway)

(def response-id-ks [:customer-gateway :customer-gateway-id])

(defrecord CustomerGateway [id-tag stack-id bgp-asn ip-address]
  Resource
  (retrieve-raw-all [this]
    (:customer-gateways (aws-ec2/describe-customer-gateways)))
  (sanitize [this]
    (dissoc this :state :customer-gateway-id :type :tags))
  (create [this]
    (let [request {:type "ipsec.1" :bgp-asn bgp-asn :public-ip ip-address}]
      (let [response (aws-ec2/create-customer-gateway request)]
        (r/add-tags (get-in response response-id-ks)
                    (merge this {:tags []})))))
  (aws-id [this]
    (:customer-gateway-id (r/retrieve-resource)))
  (delete [this]
    (aws-ec2/delete-customer-gateway {:customer-gateway-id (r/aws-id this)}))
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
