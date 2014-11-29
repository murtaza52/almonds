(ns almonds.resource-test
  (:require [midje.sweet :refer [facts fact]]
            [almonds.resource :as r]
            [almonds.customer-gateway :as cg]
            [amazonica.aws.ec2 :as aws-ec2]
            [clojure.set :as set :refer [difference]]
            [schema.core :as schema]))



;; (comment

;;   ;; stage test
;;   (def v  (vpc/map->VPC {:type :customer-gateway :almonds-tags :my-vpc}))

;;   (def v2  (vpc/map->VPC {:type :customer-gateway :almonds-tags :my-vpc2}))

;;   (r/stage :central [v v2]))

(comment  (r/get-almonds-resources :central :customer-gateway))

(def retrieved-json {:state "available",
                     :type "ipsec.1",
                     :customer-gateway-id "cgw-a48c6ecd",
                     :tags
                     [{:value "CentralVpcCustomerGatewayBackup", :key "almonds-tags"}
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

;;(def id1 (r/uuid))

(defn customer-gateway-factory [{:keys [bgp-asn ip-address tags] :or {tags []}}]
  {:bgp-asn bgp-asn :ip-address ip-address :almond-tags tags :almonds-tags (r/uuid) :almonds-type :customer-gateway})

(def my-resources  [{:almonds-type :customer-gateway
                     :almonds-tags [:sandbox-stack :web-tier :sync-box 1]
                     :bgp-asn 6500
                     :ip-address "125.12.14.111"}
                    {:almonds-type cg/type-id
                     :almonds-tags [:sandbox-stack :web-tier :sync-box]
                     :bgp-asn 6500
                     :ip-address "122.12.14.214"}
                    {:almonds-type cg/type-id :bgp-asn 6500 :ip-address "122.12.15.215" :almonds-tags [:sandbox-stack :app-tier :sync-box]}
                    {:almonds-type cg/type-id :bgp-asn 6500 :ip-address "122.12.16.216" :almonds-tags [:sandbox-stack :app-tier :utility-box]}])

;; (comment
;;   (def retrieved-rs1 [(cg/map->CustomerGateway {:almonds-tags :g4 :bgp-asn 6500 :ip-address "122.12.13.211"})
;;                       (cg/map->CustomerGateway {:almonds-tags :g5 :bgp-asn 6500 :ip-address "122.12.13.212"})])

;;   (def stageed-rs1 [(cg/map->CustomerGateway {:almonds-tags :g4 :bgp-asn 6500 :ip-address "122.12.13.211"})
;;                      (cg/map->CustomerGateway {:almonds-tags :g7 :bgp-asn 6500 :ip-address "122.12.13.110"})])

;;   (r/diff-resources retrieved-rs1 commited-rs1))


;; (comment
;;   (r/create (cg/map->CustomerGateway {:id :g4 :bgp-asn 6500 :ip-address "122.12.13.211"})))



;;(aws-ec2/create-customer-gateway {:type "ipsec.1" :bgp-asn 65000 :public-ip "122.12.13.113"})

                                        ; (r/add-tags "cgw-a48c6ecd" [{:key "almonds-tags" :value "my-id"} {:key "stack-id" :value "my-stack"}])

;; (r/retrieve-all (vpc/map->VPC {}))


(comment  (r/sanitize-resources :customer-gateway
                                (r/get-almonds-resources :central :customer-gateway)))

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
           {:value "CentralVpcCustomerGatewayPrimary", :key "almonds-tags"}],
          :bgp-asn "65000",
          :ip-address "182.72.16.113"}])

(comment
  (r/sanitize-resources :customer-gateway rs)
  (r/diff-stack-resource :murtaza-sandbox :customer-gateway)

  @r/pushed-state )

(comment
  (r/clear-all) ;; :pull :staging :diff
  (r/unstage)
  (r/stage my-resources)
  (r/diff :murtaza-sandbox)
  (r/push :with-pull false)
  (r/pull)
  (r/pushed-resources-raw)
  (r/sanitize-resources)
  (r/create (first my-resources))
  (r/delete (first my-resources)))



(read-string "6500")
