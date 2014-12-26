(ns almonds.resources
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.utils :refer :all]
            [almonds.contract :refer :all]
            [almonds.resource :refer :all]
            [almonds.api :refer :all]
            [almonds.protocol-numbers :refer :all]
            [almonds.state :refer :all]))

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

(defn get-entries [{:keys [entries] :as m}]
  (doall
   (map (fn[acl-entry]
          (-> acl-entry
            (merge {:almonds-type :network-acl-entry
                    :almonds-tags (into #{}
                                        (concat
                                         (drop-val :network-acl (:almonds-tags m))
                                         #{:network-acl-entry (rule-type (:egress acl-entry)) (:rule-number acl-entry)}))
                    :network-acl-id (:almonds-tags m)})
            (update-in [:protocol] get-protocol-keyword)))
        entries)))

(defn create-almonds-tags-for-network-association [m]
  (into #{}
        (concat
         #{:network-acl-association}
         (drop-val :network-acl (:network-acl-id m))
         (drop-val :subnet (:subnet-id m)))))

(defn get-associations [{:keys [associations] :as acl}]
  (doall
   (map (fn[association]
          (-> association
            (merge {:almonds-type :network-acl-association
                    :network-acl-id (:almonds-tags acl)
                    :subnet-id (aws-id->almonds-tags (:subnet-id association))})
            (#(merge % {:almonds-tags (create-almonds-tags-for-network-association %)}))))
        associations)))

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
                                (add  [{:network-acl-id (:almonds-tags m),
                                        :almonds-type :network-acl-entry,
                                        :protocol :all,
                                        :rule-number 32767,
                                        :rule-action "deny",
                                        :cidr-block "0.0.0.0/0"}
                                       {:network-acl-id (:almonds-tags m),
                                        :almonds-type :network-acl-entry,
                                        :protocol :all,
                                        :rule-number 32767,
                                        :rule-action "deny",
                                        :egress true,
                                        :cidr-block "0.0.0.0/0"}])
                                (add-type-to-tags :vpc-id :vpc m))
              :dependents-fn (fn[acl]
                               (concat (get-entries acl) (get-associations acl)))
              :dependent-types [:network-acl-entry :network-acl-association]})

(defresource {:resource-type :network-acl-entry
              :create-map (fn[m]
                            (-> m
                              (update-in [:network-acl-id] aws-id)
                              (update-in [:egress] #(if % % false))
                              (dissoc :almonds-type :almonds-tags)
                              (update-in [:protocol] get-protocol-num)))
              :create-fn #(if-not (default-acl-entry? %)
                            (aws-ec2/create-network-acl-entry %)
                            (println "Can not create default acl entry, it is created with the acl."))
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
                                    (assoc m :almonds-tags (into #{}
                                                                 (concat
                                                                  #{:network-acl-entry (rule-type (:egress m)) (:rule-number m)}
                                                                  (drop-val :network-acl (:network-acl-id m)))))))
                                 ((fn[m] (add-type-to-tags :network-acl-id :network-acl m)))
                                 ((fn[m] (if (:egress m) m (dissoc m :egress)))))
              :create-tags? false
              :is-dependent? true})

(defn get-network-acl-association-id [subnet-id]
  (let [subnet-aws-id (aws-id (add-type :subnet subnet-id))]
    (->> (get-remote-raw)
      (filter #(has-key? :network-acl-id %))
      (mapcat :associations)
      (filter #(has-key-val? :subnet-id subnet-aws-id %))
      first
      :network-acl-association-id)))

(comment (get-network-acl-association-id [:sandbox :web-tier :web-server :subnet]))

(defresource {:resource-type :network-acl-association
              :create-map (fn[m]
                            (-> m
                              (update-in [:network-acl-id] aws-id)
                              (assoc :association-id (get-network-acl-association-id (:subnet-id m)))
                              (dissoc :almonds-type :almonds-tags :subnet-id)))
              :create-fn aws-ec2/replace-network-acl-association
              :delete-fn-alternate (fn[m]
                                     (println "Network association can not be deleted but only replaced, thus unable to delete it.")) 
              :sanitize-ks [:network-acl-association-id]
              :pre-staging-fn #(-> %
                                 ((fn[m]
                                    (assoc m :almonds-tags (create-almonds-tags-for-network-association m))))
                                 ((fn[m] (add-type-to-tags :network-acl-id :network-acl m)))
                                 ((fn[m] (add-type-to-tags :subnet-id :subnet m))))
              :create-tags? false
              :is-dependent? true})

(defresource {:resource-type :subnet
              :create-map #(hash-map :availability-zone (:availability-zone %) :cidr-block (:cidr-block %) :vpc-id (aws-id (:vpc-id %)))
              :create-fn aws-ec2/create-subnet
              :validate-fn (constantly true)
              :sanitize-ks [:available-ip-address-count]
              :sanitize-fn #(update-in % [:vpc-id] aws-id->almonds-tags)
              :describe-fn aws-ec2/describe-subnets
              :aws-id-key :subnet-id
              :delete-fn aws-ec2/delete-subnet
              :dependents-fn (constantly '())
              :pre-staging-fn (fn[m] (add-type-to-tags :vpc-id :vpc m))})

(defresource {:resource-type :security-group
              :create-map (fn[m]
                            (as-> m m
                              (update-in m [:vpc-id] aws-id)
                              (dissoc m :almonds-tags :almonds-tags)))
              :create-fn aws-ec2/create-security-group
              :sanitize-ks [:ip-permissions :owner-id :ip-permissions-egress]
              :sanitize-fn #(update-in % [:vpc-id] aws-id->almonds-tags)
              :describe-fn aws-ec2/describe-security-groups
              :aws-id-key :group-id
              :delete-fn aws-ec2/delete-security-group
              :dependents-fn (constantly '())
              :pre-staging-fn (fn[m]
                                (as-> m m
                                  (add-type-to-tags :vpc-id :vpc m)
                                  (merge {:group-name (tags->name (:almonds-tags m))} m)
                                  (merge {:description (tags->name (:almonds-tags m))} m)))})




;; (defresource {:resource-type :instance
;;               :create-map #(hash-map :availability-zone (:availability-zone %) :cidr-block (:cidr-block %) :vpc-id (aws-id (:vpc-id %)))
;;               :create-fn aws-ec2/create-subnet
;;               :validate-fn (constantly true)
;;               :sanitize-ks [:available-ip-address-count]
;;               :sanitize-fn #(update-in % [:vpc-id] aws-id->almonds-tags)
;;               :describe-fn-alternate (fn[]
;;                                        (->> (aws-ec2/describe-instances)
;;                                             vals
;;                                             first
;;                                             (mapcat vals)
;;                                             (map first)))
;;               :aws-id-key :subnet-id
;;               :delete-fn aws-ec2/delete-subnet
;;               :dependents-fn (constantly '())
;;               :pre-staging-fn (fn[m] (add-type-to-tags :vpc-id :vpc m))})

;; (aws-ec2/describe-instances)

{:ami-id "ami-2058e248"
 :availability-zone "us-east-1b"
 }
