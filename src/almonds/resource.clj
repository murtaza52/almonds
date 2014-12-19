(ns almonds.resource
  (:require [almonds.api :refer :all]
            [almonds.utils :refer :all]
            [almonds.state :refer :all]
            [almonds.contract :refer :all]))

(def empty-seq '(constantly (rest '())))

(def true-fn '(constantly true))

(defmacro defresource
  [{:keys [resource-type create-map create-fn validate-fn sanitize-ks describe-fn aws-id-key delete-fn sanitize-fn dependents-fn pre-staging-fn create-tags? delete-fn-alternate]
    :or {pre-staging-fn identity
         create-tags? true
         delete-fn false
         delete-fn-alternate false
         validate-fn true-fn
         sanitize-ks empty-seq
         sanitize-fn identity
         describe-fn empty-seq
         aws-id-key nil
         dependents-fn empty-seq}}]
  `(do
     (when-not (coll-contains? ~resource-type @resource-types)
       (swap! resource-types conj ~resource-type))
     (defmethod validate ~resource-type [m#]
       (~validate-fn m#))
     (defmethod create ~resource-type [m#]
       (when-let [response# (~create-fn (~create-map m#))]
         (swap! pushed-state conj (-> response#
                                        vals
                                        first
                                        (#(merge % {:almonds-aws-id (% ~aws-id-key)}))
                                        (#(merge % {:almonds-tags (:almonds-tags m#)}))
                                        (#(merge % {:almonds-type (:almonds-type m#)}))))
         (when ~create-tags? (->> m#
                                  almonds-tags
                                  almonds->aws-tags
                                  (create-tags (aws-id (:almonds-tags m#)))))))
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
