(ns almonds.customer-gateway
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.resource :as r :refer [validate create delete sanitize retrieve-all aws-id pushed-resources-raw ->almond-map resource-types aws-id-key]]))

(def type-id :customer-gateway)

(defmacro defresource
  [{:keys [resource-type create-map create-fn validate-fn sanitize-ks describe-fn aws-id-key delete-fn sanitize-fn]}]
  `(do
     (swap! resource-types conj ~resource-type)
     (defmethod validate ~resource-type [m#]
       (~validate-fn m#))
     (defmethod create ~resource-type [m#]
       (let [response# (~create-fn (~create-map m#))]
         (swap! r/pushed-state conj (-> response#
                                        vals
                                        first
                                        (#(merge % {:almonds-aws-id (~aws-id-key %)}))
                                        (#(merge % {:almonds-tags (:almonds-tags m#)}))
                                        (#(merge % {:almonds-type (:almonds-type m#)}))))
         (->> m#
              r/almonds-tags
              r/almonds->aws-tags
              (r/create-tags (aws-id (:almonds-tags m#))))))
     (defmethod sanitize ~resource-type [m#]
       (-> (apply dissoc m# (conj ~sanitize-ks :tags :state :almonds-aws-id ~aws-id-key))
           (~sanitize-fn)))
     (defmethod retrieve-all ~resource-type [_#]
       (-> (~describe-fn) vals first))
     (defmethod delete ~resource-type [m#]
       (~delete-fn {~aws-id-key (aws-id (:almonds-tags m#))}))
     (defmethod aws-id-key ~resource-type [_#]
       ~aws-id-key)))

(defresource {:resource-type :customer-gateway
              :create-map #(hash-map :type "ipsec.1" :bgp-asn (:bgp-asn %) :public-ip (:ip-address %))
              :create-fn aws-ec2/create-customer-gateway
              :validate-fn (constantly true)
              :sanitize-ks [:type]
              :sanitize-fn #(update-in % [:bgp-asn] read-string)
              :describe-fn aws-ec2/describe-customer-gateways
              :aws-id-key :customer-gateway-id
              :delete-fn aws-ec2/delete-customer-gateway})

(defresource {:resource-type :vpc
              :create-map #(hash-map :cidr-block (:cidr-block %) :instance-tenancy (:instance-tenancy %))
              :create-fn aws-ec2/create-vpc
              :validate-fn (constantly true)
              :sanitize-ks [:dhcp-options-id]
              :sanitize-fn identity
              :describe-fn aws-ec2/describe-vpcs
              :aws-id-key :vpc-id
              :delete-fn aws-ec2/delete-vpc})

(defresource {:resource-type :subnet
              :create-map #(hash-map :availability-zone (:availability-zone %) :cidr-block (:cidr-block %) :vpc-id (aws-id (:vpc-id %)))
              :create-fn aws-ec2/create-subnet
              :validate-fn (constantly true)
              :sanitize-ks [:available-ip-address-count]
              :sanitize-fn #(update-in % [:vpc-id] r/aws-id->almonds-tags)
              :describe-fn aws-ec2/describe-subnets
              :aws-id-key :subnet-id
              :delete-fn aws-ec2/delete-subnet})



;; (def ordered-resources [vpc customer-gateway])

;; (doseq [r ordered-resources]
;;   (defresource r))
