(ns almonds.resource-test
  (:require [midje.sweet :refer [facts fact]]
            [almonds.subnet :as s]
            [almonds.resource :as r]
            [almonds.vpc :as vpc]
            [almonds.customer-gateway :as cg]
            [amazonica.aws.ec2 :as aws-ec2]
            [clojure.set :as set :refer [difference]]))

set/intersection

(def v  (vpc/map->VPC {:type :customer-gateway :id-tag :my-vpc}))

(def v2  (vpc/map->VPC {:type :customer-gateway :id-tag :my-vpc2}))

(r/commit :central [v v2])



(r/get-stack-resources :central :customer-gateway)

(reset! r/commit-state {})

(def retrieved-json {:state "available",
                     :type "ipsec.1",
                     :customer-gateway-id "cgw-a48c6ecd",
                     :tags
                     [{:value "CentralVpcCustomerGatewayBackup", :key "id-tag"}
                      {:value "Central VPC - Backup", :key "Name"}
                      {:value "CentralVpcTwVpn", :key "aws:cloudformation:stack-name"}
                      {:value
                      "arn:aws:cloudformation:us-east-1:790378854888:stack/CentralVpcTwVpn/1c131880-6993-11e4-bc94-50fa5262a89c",
                       :key "aws:cloudformation:stack-id"}
                      {:value "central", :key "stack-tag"}
                      {:value "CentralVpcCustomerGatewayBackup", :key "aws:cloudformation:logical-id"}],
                     :bgp-asn "65000",
                     :ip-address "14.140.43.50"})

;; (r/sanitize (cg/map->CustomerGateway {}) retrieved-json)

(defn sample-stack [ip-address]
  [{:type :customer-gateway :bgp-asn 6500 :ip-address ip-address}
   {:type :instance}])

(def my-resources  [{:id-tag :g1 :type cg/type-id :bgp-asn 6500 :ip-address "122.12.14.214"}
                    {:id-tag :g2 :type cg/type-id :bgp-asn 6500 :ip-address "122.12.15.215"}
                    {:id-tag :g3 :type cg/type-id :bgp-asn 6500 :ip-address "122.12.16.216"}])

;; to create
;; to update
;; to delete

;; only in first - to delete
;; only in second - to create
;; in both - to update

(def retrieved-rs1 [(cg/map->CustomerGateway {:id-tag :g4 :bgp-asn 6500 :ip-address "122.12.13.211"})
                   (cg/map->CustomerGateway {:id-tag :g5 :bgp-asn 6500 :ip-address "122.12.13.212"})])

(def commited-rs1 [(cg/map->CustomerGateway {:id-tag :g4 :bgp-asn 6500 :ip-address "122.12.13.215"})
                  (cg/map->CustomerGateway {:id-tag :g7 :bgp-asn 6500 :ip-address "122.12.13.110"})])

(r/diff-resources retrieved-rs1 commited-rs1)


(set/intersection #{:a :b :c} #{:a :d})

(comment
  (r/create (cg/map->CustomerGateway {:id :g4 :bgp-asn 6500 :ip-address "122.12.13.211"})))



;;(aws-ec2/create-customer-gateway {:type "ipsec.1" :bgp-asn 65000 :public-ip "122.12.13.113"})

                                        ; (r/add-tags "cgw-a48c6ecd" [{:key "id-tag" :value "my-id"} {:key "stack-tag" :value "my-stack"}])

;; (r/retrieve-raw-all (vpc/map->VPC {}))


(r/sanitize-resources :customer-gateway
                      (r/get-stack-resources :central :customer-gateway))
