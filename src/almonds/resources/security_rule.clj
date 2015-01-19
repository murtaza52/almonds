(ns almonds.resources.security-rule
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.utils :refer :all]
            [almonds.contract :refer :all]
            [almonds.api :refer :all]
            [almonds.protocol-numbers :refer :all]
            [almonds.state :refer :all]))

(defmethod validate :security-rule [m]
  true)

(defn ingress-request [m]
  (-> m
      (update-in [:group-id] aws-id)
      (dissoc :almonds-type :egress :almonds-tags)))

(defn egress-request [m]
  {:group-id (aws-id (:group-id m))
   :ip-permissions [{:ip-protocol (:ip-protocol m)
                     :from-port (:from-port m)
                     :to-port (:to-port m)
                     :ip-ranges [{:cidr-ip (:cidr-ip m)}]}]})

(defmethod create :security-rule [m]
  (if (:egress m)
    (aws-ec2/authorize-security-group-egress (egress-request m))
    (aws-ec2/authorize-security-group-ingress (ingress-request m))))

(defmethod parent-type :security-rule [_]
  :security-group)

(defmethod sanitize :security-rule [m]
  (-> m
      (dissoc :ip-ranges :ip-permissions :owner-id :ip-permissions-egress :user-id-group-pairs)
      (update-in [:group-id] aws-id->almonds-tags)))

(defmethod retrieve-all :security-rule [_]
  nil)

(defmethod delete :security-rule [m]
  (if (:egress m)
    (aws-ec2/revoke-security-group-egress (egress-request m))
    (aws-ec2/revoke-security-group-ingress (ingress-request m))))

(defmethod aws-id-key :security-rule [_]
  :group-id)

(defmethod dependents :security-rule [m]
  [])

(defn add-default-egress-field [m]
  (if-not (contains? m :egress)
    (assoc m :egress false)
    m))

(add-default-egress-field {:a 2 :egress true})

(defmethod pre-staging :security-rule [m]
  (as-> m m
    (add-type-to-tags :group-id :security-group m)
    (add-default-egress-field m)))

(defmethod prepare-almonds-tags :security-rule [m]
  (-> m
      (assoc :almonds-tags 
             (remove-nils-from-tags
              (conj (drop-val :security-group (:group-id m))
                    (:egress m)
                    (:cidr-ip m)
                    (:ip-protocol m)
                    (:from-port m)
                    (:to-port m))))
      (default-prepare-almonds-tags)))

(defmethod is-dependent? :security-rule [_]
  true)

(defmethod dependent-types :security-rule [_]
  [])

