(ns almonds.resources.security-group
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.utils :refer :all]
            [almonds.contract :refer :all]
            [almonds.api :refer :all]
            [almonds.protocol-numbers :refer :all]
            [almonds.state :refer :all]
            [slingshot.slingshot :refer [throw+]]))

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

(defn instance-has-security-group? [{:keys [security-group-ids] :as instance} {:keys [almonds-tags] :as security-group}]
  (if (seq (filter #(= % almonds-tags) security-group-ids))
    true
    false))

(comment (instance-has-security-group?
          {:security-group-ids '(#{:security-group 2 :classic})}
          {:almonds-tags #{:security-group 2 :classic}}))

(defn find-instance-using-group [security-group]
  (filter #(instance-has-security-group? % security-group) (get-remote :instance)))

(comment (find-instance-using-group {:description "Security_Group; 2; Classic",
                                     :tags
                                     [{:value "#{:security-group 2 :classic}", :key ":almonds-tags"}
                                      {:value ":default", :key ":almonds-stack"}
                                      {:value "Security_Group; 2; Classic", :key "Name"}
                                      {:value ":security-group", :key ":almonds-type"}],
                                     :ip-permissions [],
                                     :group-id "sg-c104c6ac",
                                     :almonds-tags #{:security-group 2 :classic},
                                     :almonds-type :security-group,
                                     :almonds-stack :default,
                                     :group-name "Security_Group; 2; Classic",
                                     :ip-permissions-egress [],
                                     :owner-id "790378854888",
                                     :almonds-aws-id "sg-c104c6ac"}))

(defn security-group-is-not-attached? [m]
  (not (if (seq (find-instance-using-group m)) true false)))

(comment (security-group-is-not-attached? {:description "Security_Group; 2; Classic",
                                           :tags
                                           [{:value "#{:security-group 2 :classic}", :key ":almonds-tags"}
                                            {:value ":default", :key ":almonds-stack"}
                                            {:value "Security_Group; 2; Classic", :key "Name"}
                                            {:value ":security-group", :key ":almonds-type"}],
                                           :ip-permissions [],
                                           :group-id "sg-c104c6ac",
                                           :almonds-tags #{:security-group 2 :classic},
                                           :almonds-type :security-group,
                                           :almonds-stack :default,
                                           :group-name "Security_Group; 2; Classic",
                                           :ip-permissions-egress [],
                                           :owner-id "790378854888",
                                           :almonds-aws-id "sg-c104c6ac"}))

(defmethod delete :security-group [m]
  (letfn [(delete-fn [] 
            (aws-ec2/delete-security-group {:group-id (aws-id (:almonds-tags m))}))] 
    (wait-on-state
     #(security-group-is-not-attached? m)
     delete-fn
     #(do
        (println "Unable to delete the security group as it is still associated with an instance. Retrieving the state of instance and retrying.")
        (pull :instance))
     #(throw+ {:msg "Unable to delete the security group as it is still attached to an instance. Either terminate the instance or detach the security group." :args m})
     #(pull :instance)
     5
     15000)))

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
       (map #(assoc % :group-id (:group-id m)))
       (map #(add-stack-key % (:almonds-stack m)))))

(comment (get-rules a))

(defmethod dependents :security-group [m]
  (get-rules m))
