(ns almonds.resources.security-group
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.utils :refer :all]
            [almonds.contract :refer :all]
            [almonds.api :refer :all]
            [almonds.protocol-numbers :refer :all]
            [almonds.state :refer :all]))

(defmethod validate :security-group [m]
  true)

(defmethod create :security-group [m]
  (let [request (-> m
                  (update-in [:vpc-id] aws-id)
                  (dissoc :almonds-tags :almonds-tags))
        response (aws-ec2/create-security-group request)
        id (:group-id response)]
    (create-aws-tags id m)))

(defmethod parent-type :security-group [_]
  nil)

(defmethod sanitize :security-group [m]
  (-> m
    (dissoc :ip-permissions :owner-id :ip-permissions-egress :group-id :tags :state :almonds-aws-id)
    (update-in [:vpc-id] aws-id->almonds-tags)))

(defmethod retrieve-all :security-group [_]
  (-> (aws-ec2/describe-security-groups)
    vals
    first))

(defmethod delete :security-group [m]
  (aws-ec2/delete-security-group {:group-id (aws-id (:almonds-tags m))}))

(defmethod aws-id-key :security-group [_]
  :group-id)

(defmethod dependents :security-group [m]
  [])

(defmethod pre-staging :security-group [m]
  (as-> m m
    (add-type-to-tags :vpc-id :vpc m)
    (merge {:group-name (tags->name (:almonds-tags m))} m)
    (merge {:description (tags->name (:almonds-tags m))} m)))

(defmethod is-dependent? :security-group [_]
  false)

(defmethod dependent-types :security-group [_]
  [])
