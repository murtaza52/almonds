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
                    (#(if (:vpc-id %) (update-in % [:vpc-id] aws-id) %))
                    (dissoc :almonds-tags :almonds-type))
        response (aws-ec2/create-security-group request)
        id (:group-id response)]
    (create-aws-tags id m)))

(defmethod parent-type :security-group [_]
  nil)

(defmethod sanitize :security-group [m]
  (if (:vpc-id m)(update-in m [:vpc-id] aws-id->almonds-tags) m))

(defmethod retrieve-all :security-group [_]
  (-> (aws-ec2/describe-security-groups)
    vals
    first))

(defmethod delete :security-group [m]
  (aws-ec2/delete-security-group {:group-id (aws-id (:almonds-tags m))}))

(defmethod aws-id-key :security-group [_]
  :group-id)

(defmethod pre-staging :security-group [m]
  (as-> m m
    (#(if (:vpc-id %) (add-type-to-tags :vpc-id :vpc %) %) m)
    (merge {:group-name (tags->name (:almonds-tags m))} m)
    (merge {:description (tags->name (:almonds-tags m))} m)))

(defmethod get-default-dependents :security-group [m]
  (if (:vpc-id m)
    [{:almonds-type :security-rule,
                    :group-id (:almonds-tags m),
                    :egress true,
                    :cidr-ip "0.0.0.0/0",
                    :ip-protocol "-1"}]
    []))

(defmethod is-dependent? :security-group [_]
  false)

(defmethod dependent-types :security-group [_]
  [:security-rule])

(defn get-rule [m egress]
  (concat
   (map #(assoc m :cidr-ip % :egress egress) (:ip-ranges m))
   (map #(assoc m :source-security-group-owner-id (:group-id %) :egress egress) (:user-id-group-pairs m))))

;; the create-almonds-tags is expecting tags in :group-id. However again reassigning the aws id for consistency.
(defn get-rules [m]
  (->> (flatten
        (concat
         (map #(get-rule % false) (:ip-permissions m))
         (map #(get-rule % true) (:ip-permissions-egress m))))
       (map #(assoc % :group-id (:almonds-tags m)))
       (map #(assoc % :almonds-type :security-rule))
       (map prepare-almonds-tags)
       (map #(assoc % :group-id (:group-id m)))))

(comment (get-rules a))

(defmethod dependents :security-group [m]
  (get-rules m))
