(ns almonds.resources.security-group
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.utils :refer :all]
            [almonds.contract :refer :all]
            [almonds.api :refer :all]
            [almonds.protocol-numbers :refer :all]
            [almonds.state :refer :all]
            [almonds.resources.security-rule :as rule]))

(defmethod validate :security-group [m]
  true)

(defmethod create :security-group [m]
  (let [request (-> m
                  (update-in [:vpc-id] aws-id)
                  (dissoc :almonds-tags :almonds-type))
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

(defmethod pre-staging :security-group [m]
  (add {:almonds-type :security-rule,
        :group-id (:almonds-tags m),
        :egress true,
        :cidr-ip "0.0.0.0/0",
        :ip-protocol "-1",})
  (as-> m m
    (add-type-to-tags :vpc-id :vpc m)
    (merge {:group-name (tags->name (:almonds-tags m))} m)
    (merge {:description (tags->name (:almonds-tags m))} m)))

(defmethod is-dependent? :security-group [_]
  false)

(defmethod dependent-types :security-group [_]
  [:security-rule])

(def a {:description "App_Box; Web_Tier; Sandbox; Security_Group",
        :tags
        [{:value "App_Box; Web_Tier; Sandbox; Security_Group", :key "Name"}
         {:value ":security-group", :key ":almonds-type"}
         {:value "#{:app-box :web-tier :sandbox :security-group}", :key ":almonds-tags"}],
        :ip-permissions
        [{:ip-protocol "tcp", :from-port 9023, :to-port 9023, :user-id-group-pairs [], :ip-ranges ["0.0.0.0/0"]}
         {:ip-protocol "tcp", :from-port 9021, :to-port 9021, :user-id-group-pairs [{:group-id "sg-0660a462", :user-id "790378854888"}], :ip-ranges []}
         {:ip-protocol "tcp", :from-port 9021, :to-port 9021, :user-id-group-pairs [], :ip-ranges ["0.0.0.0/0" "203.0.113.0/24"]}],
        :group-id "sg-0760a463",
        :almonds-tags #{:app-box :web-tier :sandbox :security-group},
        :almonds-type :security-group,
        :vpc-id "vpc-4e2f482b",
        :group-name "App_Box; Web_Tier; Sandbox; Security_Group",
        :ip-permissions-egress [{:ip-protocol "-1", :user-id-group-pairs [], :ip-ranges ["0.0.0.0/0"]}],
        :owner-id "790378854888",
        :almonds-aws-id "sg-0760a463"})

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
       (map rule/create-almonds-tags)
       (map #(assoc % :group-id (:group-id m)))))

(comment (get-rules a))

(rule/create-almonds-tags {:almonds-type :security-rule,
                           :group-id "sg-0760a463",
                           :egress false,
                           :cidr-ip "0.0.0.0/0",
                           :ip-protocol "tcp",
                           :from-port 9023,
                           :to-port 9023,
                           :user-id-group-pairs [],
                           :ip-ranges ["0.0.0.0/0"]})

(defmethod dependents :security-group [m]
  (get-rules m))
