(ns almonds.resource-test
  (:require [almonds.api :refer :all]
            [amazonica.aws.ec2 :as aws-ec2]
            [clojure.set :as set :refer [difference]]
            [schema.core :as schema]
            [almonds.contract :refer :all]
            [almonds.state :refer :all]
            [almonds.core :refer [set-aws-credentials]]))

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

(def security-group-2 {:vpc-id [:sandbox :web-tier]
                       :description "Almonds test security group"
                       :group-name "test security group"
                       :almonds-type :security-group
                       :almonds-tags [:sandbox :web-tier :app-box]})

(def security-group {:vpc-id [:sandbox :web-tier]
                     :almonds-type :security-group
                     :almonds-tags [:sandbox :web-tier :app-box]})

(def security-rule {:group-id [:sandbox :web-tier :app-box]
                    :egress false
                    :cidr-ip "27.0.0.0/0"
                    :ip-protocol "-1"
                    :from-port 7015
                    :to-port 7015
                    :almonds-type :security-rule})

(def security-rule2 {:group-name "App_Box; Web_Tier; Sandbox; Security_Group" 
                     :cidr-ip "27.0.0.0/0"
                     :ip-protocol "-1"
                     :from-port 7015
                     :to-port 7015
                     :vpc-id "vpc-4e2f482b"})

;;(aws-id #{:app-box :web-tier :sandbox :security-group})
;;(aws-id #{:sandbox :web-tier :vpc})

;;(aws-ec2/authorize-security-group-ingress security-rule2)

(add [test-vpc test-subnet test-acl acl-entry acl-association security-group])

(aws-id->almonds-tags "sg-0760a463")

(comment
  (get-local :security-ru)
  (get-remote :security-rule)
  (pull-resource :security-group)
  (add [test-vpc])
  (expel)
  (diff-tags 9023)
  (sync-resources)
  (sync-only-delete)
  (compare-resources :security-rule 9023)
  (recreate)
  (get-remote-raw :security-group)
  (clear-all)
  (sync-only-create)
  (set-already-retrieved-remote)

  (delete-resources :security-group)
  (pull-resource :security-group)
  (pull)

  (dependent-types {:almonds-type :network-acl-entry})

  (create (first (get-local :security-group)))


  @remote-state

  )



