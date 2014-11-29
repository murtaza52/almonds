(ns almonds.customer-gateway
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.resource :as r :refer [validate create delete sanitize retrieve-all aws-id pushed-resources-raw ->almond-map]]))

(def type-id :customer-gateway)

(defmacro defresource
  [{:keys [resource-type response-id-ks create-map create-fn validate-fn sanitize-ks describe-fn aws-id-key delete-fn sanitize-fn]}]
  `(do
     (defmethod validate ~resource-type [m#]
       (~validate-fn m#))
     (defmethod create ~resource-type [m#]
       (let [response# (~create-fn (~create-map m#))
             resource-id# (get-in response# ~response-id-ks)]
         (->> m#
              r/almonds-tags
              r/almonds->aws-tags
              (r/create-tags resource-id#))))
     (defmethod sanitize ~resource-type [m#]
       (-> (apply dissoc m# ~sanitize-ks)
           (~sanitize-fn)))
     (defmethod retrieve-all ~resource-type [_#]
       (-> (~describe-fn) vals first))
     (defmethod aws-id ~resource-type [m#]
       (->> m#
           :almonds-tags
           (apply pushed-resources-raw)
           first
           ~aws-id-key))
     (defmethod delete ~resource-type [m#]
       (~delete-fn {~aws-id-key (aws-id m#)}))))

(macroexpand '(defresource  {:resource-type :customer-gateway
                             :response-id-ks [:customer-gateway :customer-gateway-id]
                             :create-map #(hash-map :type "ipsec.1" :bgp-asn (:bgp-asn %) :public-ip (:ip-address %))
                             :create-fn aws-ec2/create-customer-gateway
                             :validate-fn (constantly true)
                             :sanitize-ks [:state :customer-gateway-id :type :tags]
                             :sanitize-fn #(update-in % [:bgp-asn] read-string)
                             :describe-fn aws-ec2/describe-customer-gateways
                             :aws-id-key :customer-gateway-id
                             :delete-fn aws-ec2/delete-customer-gateway}))

(defresource  {:resource-type :customer-gateway
               :response-id-ks [:customer-gateway :customer-gateway-id]
               :create-map #(hash-map :type "ipsec.1" :bgp-asn (:bgp-asn %) :public-ip (:ip-address %))
               :create-fn aws-ec2/create-customer-gateway
               :validate-fn (constantly true)
               :sanitize-ks [:state :customer-gateway-id :type :tags]
               :sanitize-fn #(update-in % [:bgp-asn] read-string)
               :describe-fn aws-ec2/describe-customer-gateways
               :aws-id-key :customer-gateway-id
               :delete-fn aws-ec2/delete-customer-gateway})
