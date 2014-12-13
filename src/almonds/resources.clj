(ns almonds.resources
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.utils :refer :all]
            [almonds.contract :refer :all]
            [almonds.resource :refer :all]
            [almonds.api :refer :all]))

(defresource {:resource-type :customer-gateway
              :create-map #(hash-map :type "ipsec.1" :bgp-asn (:bgp-asn %) :public-ip (:ip-address %))
              :create-fn aws-ec2/create-customer-gateway
              :validate-fn (constantly true)
              :sanitize-ks [:type]
              :sanitize-fn #(update-in % [:bgp-asn] read-string)
              :describe-fn aws-ec2/describe-customer-gateways
              :aws-id-key :customer-gateway-id
              :delete-fn aws-ec2/delete-customer-gateway
              :dependents-fn (constantly '())})

(defresource {:resource-type :vpc
              :create-map #(hash-map :cidr-block (:cidr-block %) :instance-tenancy (:instance-tenancy %))
              :create-fn aws-ec2/create-vpc
              :validate-fn (constantly true)
              :sanitize-ks [:dhcp-options-id]
              :sanitize-fn identity
              :describe-fn aws-ec2/describe-vpcs
              :aws-id-key :vpc-id
              :delete-fn aws-ec2/delete-vpc
              :dependents-fn (constantly '())})

(defresource {:resource-type :subnet
              :create-map #(hash-map :availability-zone (:availability-zone %) :cidr-block (:cidr-block %) :vpc-id (aws-id (:vpc-id %)))
              :create-fn aws-ec2/create-subnet
              :validate-fn (constantly true)
              :sanitize-ks [:available-ip-address-count]
              :sanitize-fn #(update-in % [:vpc-id] aws-id->almonds-tags)
              :describe-fn aws-ec2/describe-subnets
              :aws-id-key :subnet-id
              :delete-fn aws-ec2/delete-subnet
              :dependents-fn (constantly '())})

(defresource {:resource-type :network-acl
              :create-map #(hash-map :vpc-id (aws-id (:vpc-id %)))
              :create-fn aws-ec2/create-network-acl
              :validate-fn (constantly true)
              :sanitize-ks [:is-default :entries :associations]
              :sanitize-fn #(update-in % [:vpc-id] aws-id->almonds-tags)
              :describe-fn aws-ec2/describe-network-acls
              :aws-id-key :network-acl-id
              :delete-fn aws-ec2/delete-network-acl
              :pre-staging-fn (fn[m]
                                (stage  [{:network-acl-id (:almonds-tags m),
                                            :almonds-type :network-acl-entry,
                                            :protocol "-1",
                                            :rule-number 32767,
                                            :rule-action "deny",
                                            :cidr-block "0.0.0.0/0"}
                                           {:network-acl-id (:almonds-tags m),
                                            :almonds-type :network-acl-entry,
                                            :protocol "-1",
                                            :rule-number 32767,
                                            :rule-action "deny",
                                            :egress true,
                                            :cidr-block "0.0.0.0/0"}])
                                (add-type-to-tags :vpc-id :vpc m))
              :dependents-fn (fn[m] (->> m :entries (map #(merge % {:almonds-type :network-acl-entry
                                                                    :almonds-tags (vec (concat
                                                                                        (drop-val :network-acl (:almonds-tags m))
                                                                                        [:network-acl-entry (rule-type (:egress %)) (:rule-number %)]))
                                                                    :network-acl-id (:almonds-tags m)}))))})

(defresource {:resource-type :network-acl-entry
              :create-map (fn[m]
                            (-> m
                                (update-in [:network-acl-id] aws-id)
                                (update-in [:egress] #(if % % false))
                                (dissoc :almonds-type :almonds-tags)))
              :create-fn #(if-not (default-acl-entry? %)
                            (aws-ec2/create-network-acl-entry %)
                            (println "Can not create default acl entry, it is created with the acl."))
              :validate-fn (constantly true)
              :sanitize-ks []
              :sanitize-fn identity
              :describe-fn (constantly '())
              :aws-id-key nil
              :delete-fn-alternate (fn[m]
                                     (if-not (default-acl-entry? m)
                                       (aws-ec2/delete-network-acl-entry
                                        {:egress (:egress m) :network-acl-id (aws-id (:network-acl-id m)) :rule-number (:rule-number m)})
                                       (println "Can not delete default acl entry, it will be deleted with the acl.")))
              :dependents-fn (constantly '())
              :pre-staging-fn #(-> %
                                   ((fn[m]
                                      (assoc m :almonds-tags (vec (concat
                                                                   [:network-acl-entry (rule-type (:egress m)) (:rule-number m)]
                                                                   (drop-val :network-acl (:network-acl-id m)))))))
                                   ((fn[m] (add-type-to-tags :network-acl-id :network-acl m)))
                                   ((fn[m] (if (:egress m) m (dissoc m :egress)))))
              :create-tags? false})