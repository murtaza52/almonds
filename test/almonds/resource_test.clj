(ns almonds.resource-test
  (:require [midje.sweet :refer [facts fact]]
            [almonds.resource :as r]
            [almonds.customer-gateway :as cg]
            [amazonica.aws.ec2 :as aws-ec2]
            [clojure.set :as set :refer [difference]]))

;; (comment

;;   ;; stage test
;;   (def v  (vpc/map->VPC {:type :customer-gateway :almonds-id :my-vpc}))

;;   (def v2  (vpc/map->VPC {:type :customer-gateway :almonds-id :my-vpc2}))

;;   (r/stage :central [v v2]))

(comment  (r/get-stack-resources :central :customer-gateway))

(def retrieved-json {:state "available",
                     :type "ipsec.1",
                     :customer-gateway-id "cgw-a48c6ecd",
                     :tags
                     [{:value "CentralVpcCustomerGatewayBackup", :key "almonds-id"}
                      {:value "Central VPC - Backup", :key "Name"}
                      {:value "CentralVpcTwVpn", :key "aws:cloudformation:stack-name"}
                      {:value
                      "arn:aws:cloudformation:us-east-1:790378854888:stack/CentralVpcTwVpn/1c131880-6993-11e4-bc94-50fa5262a89c",
                       :key "aws:cloudformation:stack-id"}
                      {:value "central", :key "stack-id"}
                      {:value "CentralVpcCustomerGatewayBackup", :key "aws:cloudformation:logical-id"}],
                     :bgp-asn "65000",
                     :ip-address "14.140.43.50"})

;; (r/sanitize (cg/map->CustomerGateway {}) retrieved-json)

(defn sample-stack [ip-address]
  [{:almonds-type :customer-gateway :bgp-asn 6500 :ip-address ip-address}
   {:almonds-type :instance}])

(def my-resources  [{:almonds-id :g4 :almonds-type cg/type-id :almonds-tags [:sandbox-stack :web-tier :sync-box] :bgp-asn 6500 :ip-address "122.12.14.214"}
                    {:almonds-id :g2 :almonds-type cg/type-id :bgp-asn 6500 :ip-address "122.12.15.215" :almonds-tags [:sandbox-stack :app-tier :sync-box]}
                    {:almonds-id :g3 :almonds-type cg/type-id :bgp-asn 6500 :ip-address "122.12.16.216" :almonds-tags [:sandbox-stack :app-tier :utility-box]}])

;; (comment
;;   (def retrieved-rs1 [(cg/map->CustomerGateway {:almonds-id :g4 :bgp-asn 6500 :ip-address "122.12.13.211"})
;;                       (cg/map->CustomerGateway {:almonds-id :g5 :bgp-asn 6500 :ip-address "122.12.13.212"})])

;;   (def stageed-rs1 [(cg/map->CustomerGateway {:almonds-id :g4 :bgp-asn 6500 :ip-address "122.12.13.211"})
;;                      (cg/map->CustomerGateway {:almonds-id :g7 :bgp-asn 6500 :ip-address "122.12.13.110"})])

;;   (r/diff-resources retrieved-rs1 commited-rs1))


;; (comment
;;   (r/create (cg/map->CustomerGateway {:id :g4 :bgp-asn 6500 :ip-address "122.12.13.211"})))



;;(aws-ec2/create-customer-gateway {:type "ipsec.1" :bgp-asn 65000 :public-ip "122.12.13.113"})

                                        ; (r/add-tags "cgw-a48c6ecd" [{:key "almonds-id" :value "my-id"} {:key "stack-id" :value "my-stack"}])

;; (r/retrieve-raw-all (vpc/map->VPC {}))


(comment  (r/sanitize-resources :customer-gateway
                                (r/get-stack-resources :central :customer-gateway)))

(def rs [{:state "available",
          :type "ipsec.1",
          :customer-gateway-id "cgw-a58c6ecc",
          :tags
          [{:value "CentralVpcCustomerGatewayPrimary", :key "aws:cloudformation:logical-id"}
           {:value "CentralVpcTwVpn", :key "aws:cloudformation:stack-name"}
           {:value "Central VPC - Primary", :key "Name"}
           {:value
            "arn:aws:cloudformation:us-east-1:790378854888:stack/CentralVpcTwVpn/1c131880-6993-11e4-bc94-50fa5262a89c",
            :key "aws:cloudformation:stack-id"}
           {:value "central", :key "stack-id"}
           {:value "CentralVpcCustomerGatewayPrimary", :key "almonds-id"}],
          :bgp-asn "65000",
          :ip-address "182.72.16.113"}])

(comment
  (r/sanitize-resources :customer-gateway rs)

  (r/diff-stack-resource :murtaza-sandbox :customer-gateway))

(comment
  (reset :all) ;; :pull :staging :diff
  (r/unstage)
  (r/stage :murtaza-sandbox my-resources)
  (r/diff-stack :murtaza-sandbox)
  (r/push :with-pull false)
  (r/pull :murtaza-sandbox)
  (r/show-pull-state :murtaza-sandbox))

(read-string "6500")
