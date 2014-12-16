(ns almonds.resource-test
  (:require [midje.sweet :refer [facts fact]]
            [almonds.api :refer :all]
            [amazonica.aws.ec2 :as aws-ec2]
            [clojure.set :as set :refer [difference]]
            [schema.core :as schema]
            [almonds.contract :refer :all]
            [almonds.state :refer :all]))

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


(def my-resources  [{:almonds-type :customer-gateway
                     :almonds-tags [:sandbox-stack :web-tier :sync-box 1]
                     :bgp-asn 6500
                     :ip-address "125.12.14.111"}
                    {:almonds-type :customer-gateway
                     :almonds-tags [:sandbox-stack :web-tier :sync-box]
                     :bgp-asn 6500
                     :ip-address "122.12.14.214"}
                    {:almonds-type :customer-gateway :bgp-asn 6500 :ip-address "122.12.15.215" :almonds-tags [:sandbox-stack :app-tier :sync-box]}
                    {:almonds-type :customer-gateway :bgp-asn 6500 :ip-address "122.12.16.216" :almonds-tags [:sandbox-stack :app-tier :utility-box]}])

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

;; (retrieve-all (vpc/map->VPC {}))


(comment  (sanitize-resources :customer-gateway
                                (get-almonds-resources :central :customer-gateway)))


(comment
  (sanitize-resources :customer-gateway rs)
  (diff-stack-resource :murtaza-sandbox :customer-gateway)

  @pushed-state)

(def my-resources [{:almonds-type :vpc
                    :almonds-tags [:sandbox :web-tier]
                    :cidr-block "10.2.0.0/16"
                    :instance-tenancy "default"}

                   {:almonds-type :vpc
                    :almonds-tags [:sandbox :app-tier]
                    :cidr-block "10.4.0.0/16"
                    :instance-tenancy "default"}

                   {:almonds-type :subnet
                    :almonds-tags [:sandbox :web-tier :web-server]
                    :cidr-block "10.2.11.0/25"
                    :availability-zone "us-east-1b"
                    :vpc-id [:sandbox :web-tier]}

                   {:almonds-type :subnet
                    :almonds-tags [:sandbox :app-tier :app-server]
                    :cidr-block "10.4.0.0/26"
                    :availability-zone "us-east-1b"
                    :vpc-id [:sandbox :app-tier]}])

(def vpc [{:almonds-type :vpc
           :almonds-tags [:sandbox :web-tier 1]
           :cidr-block "10.2.0.0/16"
           :instance-tenancy "default"}])

(def acls [{:almonds-type :network-acl :almonds-tags [:first] :vpc-id [:sandbox :web-tier 1]}])

(def acl-entries [{:almonds-type :network-acl-entry
                   :egress false
                   :protocol "-1"
                   :rule-action "allow"
                   :port-range {:from 22 :to 22}
                   :cidr-block "0.0.0.0/0"
                   :rule-number 3
                   :network-acl-id [:first]}])
(comment
  (clear-all) ;; :pull :staging :diff
  (unstage)
  ;;(stage my-resources)
  (staged-resources :app-tier)
  (diff)
  (push)
  (pull)
  (pushed-resources-raw :network-acl)
  (pushed-resources :network-acl-entry)
  (pushed-resources-tags)
  (sanitize-resources)
  (create (first my-resources))
  (delete (first my-resources))
  (-> (compare-resources :app-tier :subnet) :pushed first (delete))
  (stage my-resources)
  (stage vpc)
  (stage acls)
  (stage acl-entries)
  (compare-resources :app-tier :subnet)
  (recreate-inconsistent :app-tier :subnet)
  (recreate :app-tier)
  (aws-id #{1 :web-tier :sandbox :vpc})

  (delete-resources :web-tier)
  ;; compare ;; compare-inconsistent

  ;; pay for sevenolives

  (stage my-resources)
  (stage acls)
  (stage acl-entries)

  ( / (* 200 5 26 ) 40)

  ()
)

;; (aws-id (pushed-resources 1))
;; order, reader macros, delayed ID
