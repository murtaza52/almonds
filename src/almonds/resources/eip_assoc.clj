(ns almonds.resources.eip-assoc
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.utils :refer :all]
            [almonds.contract :refer :all]
            [almonds.api :refer :all]
            [almonds.api-utils :refer :all]
            [almonds.state :refer :all]
            [slingshot.slingshot :refer [throw+ try+]]))

(defmethod validate :eip-assoc [m]
  true)

(defn prepare-request [m]
  (as-> m m
    (update-in m [:instance-id] aws-id)
    (dissoc m :almonds-tags :almonds-type)))

(defmethod create :eip-assoc [m]
  (letfn [(create-fn []
            (aws-ec2/associate-address (prepare-request m)))]
    (wait-on-state
     #(is-running? (first (apply get-remote (:instance-id m))))
     create-fn
     #(pull :instance)
     #(throw+ {:msg "Unable to associate elastic IP with the instance as it is not running." :args m})
     #(pull :instance)
     5
     10000)))

(defmethod prepare-almonds-tags :eip-assoc [m]
  (as-> m m
    (assoc m :almonds-tags (cons (:public-ip m) (drop-val :instance (:instance-id m))))
    (default-prepare-almonds-tags m)))

(defmethod parent-type :eip-assoc [_]
  :elastic-ip)

(defmethod sanitize :eip-assoc [m]
  (if (ec2-classic? m)
    (update-in m [:instance-id] aws-id->almonds-tags)
    m))

(defmethod retrieve-all :eip-assoc [_]
  nil)

(defmethod delete :eip-assoc [m]
  (if (ec2-classic? m)
      (aws-ec2/disassociate-address {:public-ip (:public-ip m)})
      (aws-ec2/disassociate-address {:association-id (:association-id m)})))

(defmethod pre-staging :eip-assoc [m]
  m)

(defmethod get-default-dependents :eip-assoc [m]
  [])

(defmethod is-dependent? :eip-assoc [_]
  true)

(defmethod dependent-types :eip-assoc [_]
  [])

(defmethod dependents :eip-assoc [m]
  [])
