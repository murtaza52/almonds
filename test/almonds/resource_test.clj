(ns almonds.resource-test
  (:require [almonds.api :refer :all]
            [amazonica.aws.ec2 :as aws-ec2]
            [clojure.set :as set :refer [difference]]
            [schema.core :as schema]
            [almonds.contract :refer :all]
            [almonds.state :refer :all]
            [almonds.core :refer [set-aws-credentials]]))

;; ;; 10 sec overview 

;; (def first-vpc {:almonds-type :vpc
;;                :almonds-tags [:sandbox :web-tier]
;;                :cidr-block "10.6.0.0/16"
;;                :instance-tenancy "default"})

;; (add first-vpc)

;; (expel :vpc)

;; (diff)

;; (sync-resources :vpc :sandbox)

;; (sync-only-create)

;; (get-local-tags)

;; (get-remote-tags)

;; (delete-resources)

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ;;; rationale 

;; ;; state almonds inspect

;; (expel)

;; (add first-vpc)

;; (diff-tags)

;; (sync-resources)

;; (get-local :vpc)
;; (get-remote :vpc)
;; (get-remote-raw :vpc)
;; (compare-resources :vpc)
;; (compare-resources-raw :vpc)


;; workflow

;; define


;; add


;; sync


;; modify


;; sync




(def my-resources [{:almonds-type :vpc
                    :almonds-tags [:sandbox :web-tier]
                    :cidr-block "10.2.0.0/16"
                    :instance-tenancy "default"}

                   {:almonds-type :vpc
                    :almonds-tags [:sandbox :app-tier]
                    :cidr-block "10.5.0.0/16"
                    :instance-tenancy "default"}

                   {:almonds-type :subnet
                    :almonds-tags [:sandbox :web-tier :web-server]
                    :cidr-block "10.2.11.0/25"
                    :availability-zone "us-east-1b"
                    :vpc-id [:sandbox :web-tier]}

                   {:almonds-type :subnet
                    :almonds-tags [:sandbox :app-tier :app-server]
                    :cidr-block "10.5.0.0/26"
                    :availability-zone "us-east-1b"
                    :vpc-id [:sandbox :app-tier]}])

(def test-subnet {:almonds-type :subnet
                  :almonds-tags [:sandbox :web-tier :web-server]
                  :cidr-block "10.3.11.0/25"
                  :availability-zone "us-east-1b"
                  :vpc-id [:sandbox :web-tier]})

(def test-acl {:almonds-type :network-acl :almonds-tags [:sandbox :web-tier :web-server] :vpc-id [:sandbox :web-tier]})

(def acl-entry {:almonds-type :network-acl-entry
                :egress false
                :protocol :tcp
                :rule-action "allow"
                :port-range {:from 22 :to 22}
                :cidr-block "0.0.0.0/0"
                :rule-number 3
                :network-acl-id [:sandbox :web-tier :web-server]})

(def acl-association {:almonds-type :network-acl-association
                      :network-acl-id [:sandbox :web-tier :web-server]
                      :subnet-id [:sandbox :web-tier :web-server]})

(def test-vpc {:almonds-type :vpc
               :almonds-tags [:sandbox :web-tier]
               :cidr-block "10.3.0.0/16"
               :instance-tenancy "default"})


;; add resources
;; (add [test-vpc test-subnet test-acl acl-entry acl-association])

;; (diff-tags)

;; (expel :vpc)

(def security-group-2 {:vpc-id [:sandbox :web-tier]
                       :description "Almonds test security group"
                       :group-name "test security group"
                       :almonds-type :security-group
                       :almonds-tags [:sandbox :web-tier :app-box]})

(def security-group {:vpc-id [:sandbox :web-tier]
                     :almonds-type :security-group
                     :almonds-tags [:sandbox :web-tier :app-box]})

(def security-group-classic {:almonds-type :security-group
                             :almonds-tags [:classic 2]})

(def security-rule {:group-id [:sandbox :web-tier :app-box]
                    :egress false
                    :cidr-ip "27.0.0.0/0"
                    :ip-protocol "-1"
                    :from-port 7015
                    :to-port 7015
                    :almonds-type :security-rule})

(def security-rule2 {:almonds-type :security-rule
                     :cidr-ip "27.0.0.0/0"
                     :ip-protocol "tcp"
                     :from-port 7015
                     :to-port 7015
                     :egress false
                     :group-id [:classic 2]})

;; needs a vpc
(def security-rule-ingress {:almonds-type :security-rule
                            :cidr-ip "74.125.68.139"
                            :ip-protocol "tcp"
                            :from-port 6213
                            :to-port 80
                            :egress true
                            :group-id [:classic 2]})

(def instance {:almonds-type :instance
               :almonds-tags [:dev-box]
               :security-group-ids [[:classic 2]]
               :key-name "ac"
               :instance-type "t1.micro"
               :instance-initiated-shutdown-behavior "stop"
               :image-id "ami-66d0b40e"})

(def instance2 {:almonds-type :instance
                :almonds-tags [:dev-box 2]
                :security-group-ids [[:classic 2]]
                :key-name "ac"
                :instance-type "t1.micro"
                :instance-initiated-shutdown-behavior "stop"
                :image-id "ami-66d0b40e"})

(def eip-assoc {:almonds-type :eip-assoc :instance-id [:dev-box 2] :public-ip "107.22.188.118"})

;;(aws-id #{:app-box :web-tier :sandbox :security-group})
;;(aws-id #{:sandbox :web-tier :vpc})

;;(aws-ec2/authorize-security-group-ingress security-rule2)

;;(add [test-vpc test-subnet test-acl acl-entry acl-association])

;;(aws-id->almonds-tags "sg-0760a463")

(comment
  (pull)
  (add [test-vpc test-subnet test-acl acl-entry acl-association security-group-classic security-rule2 instance2 eip-assoc])
  (add security-group)
  (add [{:almonds-type :security-rule, :group-id [:sandbox :web-tier :app-box], :egress true, :cidr-ip "0.0.0.0/0", :ip-protocol "-1"}])
  (add test-subnet)
  (get-remote-raw :security-group)
  (get-remote :elastic-ip)
  (pull-resource :elastic-ip)
  (add test-vpc)
  (expel)
  (diff-tags)
  (sync-resources)
  (sync-only-create)
  (compare-resources :instance)
  (get-remote-raw :security-group)
  (clear-all)
  (sync-only-create)
  (set-already-retrieved-remote)
  (delete-deps-aws-id "vpc-b61f7cd3")
  (delete-resources)
  (pull-resource :network-acl)
  (pull)
  (recreate)
  (dependents {:almonds-tags #{:elastic-ip "107.22.188.118"}, :almonds-type :elastic-ip, :domain "standard", :public-ip "107.22.188.118", :instance-id ""})
  (dependent-types {:almonds-type :network-acl-entry})
  
  (aws-id #{:security-group 2 :classic})
  (aws-id #{:instance 2 :dev-box})
  (create (first (get-local :security-group)))
  @remote-state)

;; remove inconsistent 
;; select -> :data-source :local / atom / file, :tags [:a :b], :flags [:only-in-local :only-in-remote :inverse :tags :compare], 
;; apply operation - create / delete / recreate
;; 

;; write the example below in clojure, shell / json, ruby.

;; start as a service
;; run add / expel operations

;; will call multiple add operations for each stacks

;; reset-all
;; add file1.edn, file2.edn, file3.edn
;; select :only-in-local | create
;; select :only-in-remote | delete
;; select :data-source :local :tags [:dev]| if ip-address == a | recreate


