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
                :protocol "6"
                :rule-action "allow"
                :port-range {:from 22 :to 22}
                :cidr-block "0.0.0.0/0"
                :rule-number 3
                :network-acl-id [:sandbox :web-tier :web-server]})

(def test-vpc {:almonds-type :vpc
               :almonds-tags [:sandbox :web-tier]
               :cidr-block "10.3.0.0/16"
               :instance-tenancy "default"})


(comment 
  (add [test-vpc test-subnet test-acl acl-entry])
  (recreate))


