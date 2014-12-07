(ns almonds.customer-gateway
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.resource :as r :refer [validate create delete sanitize retrieve-all aws-id pushed-resources-raw ->almond-map resource-types aws-id-key dependents pre-staging]]))

(def type-id :customer-gateway)

(defn drop-val [v coll]
  (clojure.set/difference (into #{} coll) #{v}))

(defn rule-type [b]
  (if (true? b) :egress :ingress))

(defmacro defresource
  [{:keys [resource-type create-map create-fn validate-fn sanitize-ks describe-fn aws-id-key delete-fn sanitize-fn dependents-fn pre-staging-fn create-tags? delete-fn-alternate]
    :or {pre-staging-fn identity create-tags? true delete-fn false delete-fn-alternate false} }]
  `(do
     (swap! resource-types conj ~resource-type)
     (defmethod validate ~resource-type [m#]
       (~validate-fn m#))
     (defmethod create ~resource-type [m#]
       (let [response# (~create-fn (~create-map m#))]
         (swap! r/pushed-state conj (-> response#
                                        vals
                                        first
                                        (#(merge % {:almonds-aws-id (% ~aws-id-key)}))
                                        (#(merge % {:almonds-tags (:almonds-tags m#)}))
                                        (#(merge % {:almonds-type (:almonds-type m#)}))))
         (when ~create-tags? (->> m#
                                  r/almonds-tags
                                  r/almonds->aws-tags
                                  (r/create-tags (aws-id (:almonds-tags m#)))))))
     (defmethod sanitize ~resource-type [m#]
       (-> (apply dissoc m# (conj ~sanitize-ks :tags :state :almonds-aws-id ~aws-id-key))
           (~sanitize-fn)))
     (defmethod retrieve-all ~resource-type [_#]
       (-> (~describe-fn) vals first))
     (defmethod delete ~resource-type [m#]
       (if-not ~delete-fn-alternate
         (~delete-fn {~aws-id-key (aws-id (:almonds-tags m#))})
         (~delete-fn-alternate m#)))
     (defmethod aws-id-key ~resource-type [_#]
       ~aws-id-key)
     (defmethod dependents ~resource-type [m#]
       (~dependents-fn m#))
     (defmethod pre-staging ~resource-type [m#]
       (~pre-staging-fn m#))))

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
              :sanitize-fn #(update-in % [:vpc-id] r/aws-id->almonds-tags)
              :describe-fn aws-ec2/describe-subnets
              :aws-id-key :subnet-id
              :delete-fn aws-ec2/delete-subnet
              :dependents-fn (constantly '())})

(defresource {:resource-type :network-acl
              :create-map #(hash-map :vpc-id (aws-id (:vpc-id %)))
              :create-fn aws-ec2/create-network-acl
              :validate-fn (constantly true)
              :sanitize-ks [:is-default :entries :associations]
              :sanitize-fn #(update-in % [:vpc-id] r/aws-id->almonds-tags)
              :describe-fn aws-ec2/describe-network-acls
              :aws-id-key :network-acl-id
              :delete-fn aws-ec2/delete-network-acl
              :pre-staging-fn (fn[m] (r/stage  [{:network-acl-id (:almonds-tags m),
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
                                                 :cidr-block "0.0.0.0/0"}]))
              :dependents-fn (fn[m] (->> m :entries (map #(merge % {:almonds-type :network-acl-entry
                                                                    :almonds-tags (apply conj
                                                                                         #{:network-acl-entry (rule-type (:egress %)) (:rule-number %)}
                                                                                         (drop-val :network-acl (:almonds-tags m)))
                                                                    :network-acl-id (:almonds-tags m)}))))})



(defresource {:resource-type :network-acl-entry
              :create-map (fn[{:keys [protocol rule-number egress cidr-block port-range rule-action network-acl-id]}]
                             (hash-map :protocol protocol :rule-number rule-number :rule-action rule-action
                                       :port-range port-range :cidr-block cidr-block :network-acl-id (aws-id network-acl-id)))
              :create-fn aws-ec2/create-network-acl-entry
              :validate-fn (constantly true)
              :sanitize-ks []
              :sanitize-fn identity
              :describe-fn (constantly '())
              :aws-id-key nil
              :delete-fn-alternate (fn[m] (aws-ec2/delete-network-acl-entry {:egress (:egress m) :network-acl-id (aws-id (:network-acl-id m)) :rule-number (:rule-number m)}))
              :dependents-fn (constantly '())
              :pre-staging-fn (fn[m]
                                (update-in m
                                           [:almonds-tags]
                                           (fn[_] (apply conj
                                                         #{:network-acl-entry (rule-type (:egress m)) (:rule-number m)}
                                                         (drop-val :network-acl (:network-acl-id m))))))
              :create-tags? false})

;; (def ordered-resources [vpc customer-gateway])

;; (doseq [r ordered-resources]
;;   (defresource r))
