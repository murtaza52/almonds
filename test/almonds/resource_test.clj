(ns almonds.resource-test
  (:require [almonds.api :refer :all]
            [amazonica.aws.ec2 :as aws-ec2]
            [clojure.set :as set :refer [difference]]
            [schema.core :as schema]
            [almonds.contract :refer :all]
            [almonds.state :refer :all]
            [almonds.api-data :refer :all]))

;; ;; 10 sec overview 

;; (def first-vpc {:almonds-type :vpc
;;                :almonds-tags [:sandbox :web-tier]
;;                :cidr-block "10.6.0.0/16"
;;                :instance-tenancy "default"})

;; (add first-vpc)

;; (expel :vpc)

;; (diff)

;; (sync-all :vpc :sandbox)

;; (sync-only-to-create)

;; (get-local-tags)

;; (get-remote-tags)

;; (delete-resources)

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ;;; rationale 

;; ;; state almonds inspect

;; (expel)

;; (add first-vpc)

;; (diff-tags)

;; (sync-all)

;; (get-local :vpc)
;; (get-remote :vpc)
;; (get-remote-raw :vpc)
;; (compare-resource :vpc)
;; (compare-resource-raw :vpc)


;; workflow

;; define


;; add


;; sync


;; modify


;; sync



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

(def security-group-classic-dev {:almonds-type :security-group
                                 :almonds-tags [:classic 2 :dev-stack]})

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
                     :from-port 7016
                     :to-port 7016
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

(def eip-assoc {:almonds-type :eip-assoc :instance-id [:dev-box] :public-ip "23.23.196.90"})

(comment
  (compare-resource :instance)
  
  (pull)
  (add [test-vpc test-subnet test-acl acl-entry security-group-classic security-rule2 instance eip-assoc])
  (add security-group-classic-dev)
  (add "/Users/murtaza/almonds_stack.clj")
  (add security-group)
  (add [{:almonds-type :security-rule, :group-id [:sandbox :web-tier :app-box], :egress true, :cidr-ip "0.0.0.0/0", :ip-protocol "-1"}])
  (add test-subnet)
  (get-remote-raw :security-rule)
  (get-local :security-rule)
  (pull-resource :elastic-ip)
  (expel)
  (diff)
  (diff-tags)
  (sync-all :security-rule)
  (sync-only-to-create)
  (diff-resource [:web-tier :sandbox :web-server :subnet])
  (get-remote)
  (clear-all)
  (sync-only-to-delete)
  (set-already-retrieved-remote)
  (delete-deps-aws-id "vpc-b61f7cd3")
  (delete-resources)
  (pull :security-rule)
  (pull)
  (recreate-resources)
  (dependents {:almonds-tags #{:elastic-ip "107.22.188.118"}, :almonds-type :elastic-ip, :domain "standard", :public-ip "107.22.188.118", :instance-id ""})
  (dependent-types {:almonds-type :network-acl-entry})
  
  (create (first (get-local :security-group)))
  @remote-state)

(comment 

  (defn HamaraList [first-one & all]
    (reify
      clojure.lang.ISeq
      (next [this] all)
      (first [this] first-one)
      (seq [this] (cons first-one all))
      (more [this] all)
      Object
      (toString [this] (print-str (cons first-one all)))))

  (def my-list (HamaraList 1 2 3))

  (count my-list)
  (first my-list)
  (rest my-list)

  (do)
  (-> (map println my-list)
      first))



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


